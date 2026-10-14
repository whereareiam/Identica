package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.messages.model.ReceivedMessage;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;
import me.whereareiam.identica.testing.environment.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * One player's journey through the network. Every expectation checks what the player's client receives and, for
 * connections and server changes, what the proxy itself reports on its console, so a journey fails when either
 * Identica or the platform behaves differently.
 *
 * <p>Message expectations only consider messages received since the previous expectation, so a prompt repeated
 * after a reconnect is not satisfied by its earlier occurrence.</p>
 */
public final class Journey {
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final Duration SETTLE = Duration.ofSeconds(3);

	private final String name;
	private final String source;
	private final SimulatedPlayer player;
	private final String initialConnection;
	private final Session session;
	private final Messages messages;
	private final Server server;
	private final ProcessConsole proxy;
	private final IdenticaMessages configured;
	private final long created;

	private String playedAs;
	private String connectedPlayer;
	private int read;
	private long console;
	private long attempted;
	private int connections;
	private String matched = "";
	private MessageText matchedMessage;

	private Journey(ScenarioContext anvil, SimulatedPlayer player, String name, String address, String proxy) {
		this.name = name;
		this.player = player;
		this.source = address != null ? "/" + address + ":" : "";
		this.playedAs = name;
		this.connectedPlayer = "[connected player] " + name + " (" + source;
		this.initialConnection = "[initial connection] " + source;
		this.session = player.capability(Session.class);
		this.messages = player.capability(Messages.class);
		this.server = player.capability(Server.class);
		this.proxy = anvil.processes().proxy(proxy).console();
		this.configured = new IdenticaMessages(anvil.processes().proxy(proxy).workDirectory().resolve(IdenticaNetwork.DATA));
		this.console = this.proxy.checkpoint();
		this.created = console;
		this.read = messages.checkpoint();
	}

	/**
	 * Creates a player that has not connected yet and joins with the described client.
	 *
	 * <pre>{@code
	 * Journey.of(anvil, Client.offline("Alice").through(LOBBY_HOST)).join().on(AUTH);
	 * }</pre>
	 */
	public static Journey of(ScenarioContext anvil, Client client) {
		SimulatedPlayer player = anvil.players().create(PlayerOptions.builder()
				.name(client.playerName())
				.login(client.login())
				.connection(client.connection())
				.build());
		String proxy = client.getProxy() != null ? client.getProxy() : anvil.definition().getEntrypoint();
		return new Journey(anvil, player, client.getUsername(), client.getAddress(), proxy);
	}

	/**
	 * Creates an offline player that has not connected yet.
	 */
	public static Journey offline(ScenarioContext anvil, String name) {
		return of(anvil, Client.offline(name));
	}

	/**
	 * Creates an offline client that has not connected yet and joins through one proxy of a cluster. Several
	 * clients may use one username, each through its own proxy.
	 */
	public static Journey offline(ScenarioContext anvil, String name, String proxy) {
		return of(anvil, Client.offline(name).at(proxy));
	}

	/**
	 * Creates a player that owns a premium account registered with the network's stand-in for Mojang and has not connected
	 * yet. It authenticates when the proxy asks for it, as a premium client does.
	 */
	public static Journey premium(ScenarioContext anvil, SessionIdentity account) {
		return of(anvil, Client.premium(account));
	}

	/**
	 * Connects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attempt() {
		attempted = proxy.checkpoint();
		session.connect();
		return this;
	}

	/**
	 * Reconnects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attemptAgain() {
		attempted = proxy.checkpoint();
		session.rejoin();
		return this;
	}

	/**
	 * Connects and expects the proxy to accept the connection.
	 */
	public Journey join() {
		session.connect();
		return accepted();
	}

	/**
	 * Reconnects after a disconnect or kick and expects the proxy to accept the connection.
	 */
	public Journey rejoin() {
		session.rejoin();
		return accepted();
	}

	/**
	 * Disconnects and expects the proxy to report it.
	 */
	public Journey leave() {
		session.disconnect();
		session.disconnected(TIMEOUT);
		reported(connectedPlayer, "has disconnected");
		return this;
	}

	/**
	 * Expects the player on a backend: observed by that server's agent and reported by the proxy.
	 */
	public Journey on(String backend) {
		PlayerIdentity identity = server.joined(backend, TIMEOUT);
		assertEquals(backend, identity.getRoute().getServer());
		reported("[server connection] " + playedAs + " -> " + backend, "has connected");
		return this;
	}

	/**
	 * Expects the player to stay on a backend for a while, without the proxy connecting it anywhere else.
	 */
	public Journey remainsOn(String backend) {
		server.stayed(backend, SETTLE);
		List<String> moves = proxy.read(console, 200).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains("[server connection] " + playedAs + " -> ") && line.contains("has connected"))
				.toList();
		assertEquals(List.of(), moves, name + " must stay on " + backend);
		assertTrue(session.state().connected(), name + " must stay connected");
		return this;
	}

	/**
	 * Lets a duration the test configured pass, such as a lockout or a validity window, before the next step.
	 * It never stands in for waiting until the proxy has acted; the other steps wait for that themselves.
	 *
	 * @param configured duration taken from the network's configuration
	 */
	public Journey waits(Duration configured) {
		sleep(configured);
		return this;
	}

	/**
	 * Expects a message, arrived after the previous expectation, that contains every line of a configured message.
	 *
	 * <pre>{@code
	 * player.sees(messages -> messages.credential().getScenario().getRegistration().getPrompt());
	 * }</pre>
	 *
	 * @param message selects the message, a line or a list of lines, from the proxy's configuration
	 */
	public Journey sees(Function<IdenticaMessages, ?> message) {
		MessageText expected = text(message);
		matched = receive(expected::in, List.of(expected));
		matchedMessage = expected;
		return this;
	}

	/**
	 * Returns what a placeholder holds in the message matched by the previous {@link #sees}, as the player reads
	 * it, such as a secret or codes the message hands out.
	 *
	 * <pre>{@code
	 * String secret = player.sees(m -> m.verification().getMethods().getTotp().getPending()).reads("secret");
	 * }</pre>
	 *
	 * @param placeholder name of the placeholder in the configured message, without braces
	 */
	public String reads(String placeholder) {
		if (matchedMessage == null) throw new IllegalStateException(name + " has not seen a message to read from");
		return matchedMessage.value(matched, placeholder);
	}

	/**
	 * Expects the message matched by the previous {@link #sees} to contain another configured text as well.
	 */
	public Journey with(Function<IdenticaMessages, ?> message) {
		MessageText expected = text(message);
		assertTrue(expected.in(matched), name + " must also read " + expected + " in: " + matched);
		return this;
	}

	/**
	 * Expects the message matched by the previous {@link #sees} not to contain a configured text.
	 */
	public Journey without(Function<IdenticaMessages, ?> message) {
		MessageText unexpected = text(message);
		assertFalse(unexpected.in(matched), name + " must not read " + unexpected + " in: " + matched);
		return this;
	}

	/**
	 * Waits for the first of several alternative messages, arrived after the previous expectation.
	 *
	 * @return position of the alternative that arrived, starting at zero
	 */
	@SafeVarargs
	public final int seesAnyOf(Function<IdenticaMessages, ?>... alternatives) {
		List<MessageText> expected = Arrays.stream(alternatives).map(this::text).toList();
		String message = receive(received -> expected.stream().anyMatch(alternative -> alternative.in(received)), expected);
		for (int index = 0; index < expected.size(); index++)
			if (expected.get(index).in(message)) return index;

		throw new IllegalStateException("No alternative matches the received message: " + message);
	}

	/**
	 * Waits for whichever comes first: one of several alternative messages, arrived after the previous expectation,
	 * or the proxy disconnecting the player, which must then give the expected reason. For a race whose loser is
	 * either answered or disconnected, depending on how the race went.
	 *
	 * <pre>{@code
	 * int outcome = player.seesAnyOfOrIsKickedWith(m -> m.identica().getEngine().getConcurrentChangeKick(),
	 *         m -> m.verification().getCommands().getConfirm().getInvalidCode());
	 * }</pre>
	 *
	 * @param reason reason the proxy gives when it disconnects the player first
	 * @return position of the alternative that arrived, starting at zero, or the number of alternatives when the
	 * player was disconnected
	 */
	@SafeVarargs
	public final int seesAnyOfOrIsKickedWith(Function<IdenticaMessages, ?> reason, Function<IdenticaMessages, ?>... alternatives) {
		List<MessageText> expected = Arrays.stream(alternatives).map(this::text).toList();
		Predicate<String> matcher = received -> expected.stream().anyMatch(alternative -> alternative.in(received));
		int first;
		try {
			first = player.awaitFirst(TIMEOUT, messages.expectReceived(matcher, read), session.expectKicked());
		} catch (IllegalStateException timeout) {
			return fail(name + " neither received " + expected + " nor was disconnected; new messages: " + unread(), timeout);
		}
		if (first == 0) return seesAnyOf(alternatives);

		kickedWith(reason);
		return alternatives.length;
	}

	/**
	 * Returns the messages received since the previous expectation, without consuming them.
	 */
	public List<String> unread() {
		List<String> history = messages.history();
		return List.copyOf(history.subList(Math.min(read, history.size()), history.size()));
	}

	/**
	 * Expects that a configured message has not arrived since the previous expectation, after giving it time to.
	 */
	public Journey doesNotSee(Function<IdenticaMessages, ?> message) {
		MessageText unexpected = text(message);
		messages.notReceived(unexpected::in, read, SETTLE);
		return this;
	}

	/**
	 * Expects the proxy to demand online authentication that this offline player cannot give: the client ends
	 * disconnected for that cause, and the proxy reports the closed initial connection without ever reporting
	 * this client as connected. Connections of another client with the same username before this journey
	 * started are not counted, and neither are later ones when this client connects from an address of its own.
	 */
	public Journey authenticationRequired() {
		reported(initialConnection, "has disconnected");
		session.kicked(TIMEOUT);
		assertEquals(DisconnectCause.AUTHENTICATION_REQUIRED, session.state().disconnectCause());
		assertFalse(session.state().connected(), name + " must not be connected");
		List<String> accepted = proxy.read(created, 2000).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains(connectedPlayer) && line.contains("has connected"))
				.toList();
		assertEquals(connections, accepted.size(), "The proxy must not accept " + name + "; console: " + accepted);
		return this;
	}

	/**
	 * Expects the proxy to name the player differently from the client from the next connection on, as when the
	 * player gets a renamed or a temporary username. The proxy reports the player's connections under that name.
	 *
	 * @param username the username the proxy gives the player
	 */
	public Journey named(String username) {
		playedAs = username;
		connectedPlayer = "[connected player] " + username + " (" + source;
		return this;
	}

	/**
	 * Expects the player's current backend to see the player under a username.
	 */
	public Journey playsAs(String username) {
		assertEquals(username, identity().getObserved().getUsername(), name + " must play as " + username);
		return this;
	}

	/**
	 * Returns the identity the player's current backend observes.
	 */
	public PlayerIdentity identity() {
		return server.identity();
	}

	/**
	 * Describes the connection state and unread messages, for diagnostics.
	 */
	public String describe() {
		return "connected=" + session.state().connected() + " kick=" + session.state().kickReason() + " unread=" + unread();
	}

	public Journey enroll(Provider provider) {
		return command("identica enroll " + provider.getId());
	}

	/**
	 * Completes Credential's two registration steps.
	 */
	public Journey register(String password) {
		command("pass " + password);
		sees(configured -> configured.credential().getScenario().getRegistration().getConfirmPrompt());
		return command("passconfirm " + password);
	}

	public Journey login(String password) {
		return command("login " + password);
	}

	public Journey command(String command) {
		messages.command(command);
		return this;
	}

	/**
	 * Sends a chat message, as the player types it without a leading slash.
	 */
	public Journey chat(String message) {
		messages.chat(message);
		return this;
	}

	/**
	 * Expects a message, arrived after the previous expectation, that contains a text, for messages that are
	 * not part of Identica's configuration, such as an addon's or a chat message.
	 */
	public Journey seesText(String text) {
		matched = receive(received -> received.contains(text), List.of(new MessageText(text)));
		matchedMessage = null;
		return this;
	}

	/**
	 * Expects that no message containing a text has arrived since the previous expectation, after giving it time to.
	 */
	public Journey doesNotSeeText(String text) {
		messages.notReceived(received -> received.contains(text), read, SETTLE);
		return this;
	}

	/**
	 * Expects the proxy to refuse the connection before accepting it, with the given message.
	 */
	public Journey refusedWith(Function<IdenticaMessages, ?> message) {
		MessageText reason = text(message);
		reported(initialConnection, "has disconnected");

		String received = session.kicked(TIMEOUT);
		assertFalse(session.state().connected(), name + " must not be connected");
		assertEquals(DisconnectCause.SERVER, session.state().disconnectCause());
		assertTrue(reason.in(received), name + " was refused with: " + received);
		return this;
	}

	/**
	 * Expects the proxy to disconnect the player for a reason. The proxy reports the disconnect and the reason, on
	 * the same console line for a single-line reason and on the following lines otherwise. The client receives the
	 * same reason, unless the proxy closed the connection while the client was switching protocol state: such a
	 * client only sees the connection end, so the reason is compared whenever the client attributes the disconnect
	 * to the server.
	 */
	public Journey kickedWith(Function<IdenticaMessages, ?> message) {
		MessageText reason = text(message);
		String disconnect = reported(connectedPlayer, "has disconnected");
		if (!disconnect.contains(reason.signature()))
			reported(reason.signature(), "");

		String received = session.kicked(TIMEOUT);
		assertFalse(session.state().connected(), name + " must be disconnected");
		if (session.state().disconnectCause() == DisconnectCause.SERVER)
			assertTrue(reason.in(received), name + " was disconnected with: " + received);
		return this;
	}

	/**
	 * Expects the proxy to refuse an attempt while the player is still logging in, once it knows who is joining,
	 * with the given message, which the client receives as the reason. The proxy never sends the player to a
	 * backend, and no message, such as a prompt, reaches the player before the refusal.
	 */
	public Journey refusedAtLoginWith(Function<IdenticaMessages, ?> message) {
		kickedWith(message);
		assertEquals(DisconnectCause.SERVER, session.state().disconnectCause(), name + " must be refused by the proxy");
		List<String> routed = proxy.read(attempted, 500).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains("[server connection] " + name + " -> "))
				.toList();
		assertEquals(List.of(), routed, name + " must be refused before reaching a backend");
		assertEquals(List.of(), unread(), name + " must be refused before receiving a message");
		return this;
	}

	/**
	 * Expects the proxy to refuse the login with one of the game's own messages, which the client translates
	 * itself, so it arrives as its translation key.
	 *
	 * @param translationKey key of the game's message, such as {@code multiplayer.disconnect.authservers_down}
	 */
	public Journey refusedWith(String translationKey) {
		String received = session.kicked(TIMEOUT);
		assertFalse(session.state().connected(), name + " must not be connected");
		assertEquals(DisconnectCause.SERVER, session.state().disconnectCause());
		assertTrue(received.contains(translationKey), name + " was refused with: " + received);
		return this;
	}

	/**
	 * Expects the proxy to disconnect the player, whatever reason it gives or does not give.
	 */
	public Journey disconnected() {
		session.disconnected(TIMEOUT);
		reported(connectedPlayer, "has disconnected");
		assertFalse(session.state().connected(), name + " must be disconnected");
		return this;
	}

	private MessageText text(Function<IdenticaMessages, ?> message) {
		return new MessageText(message.apply(configured));
	}

	/**
	 * Waits for a message after the previous expectation and moves this journey's position behind it.
	 */
	private String receive(Predicate<String> matcher, List<MessageText> expected) {
		try {
			ReceivedMessage message = messages.received(matcher, read, TIMEOUT);
			read = message.getCheckpoint();
			return message.getText();
		} catch (IllegalStateException timeout) {
			return fail(name + " did not receive " + expected + "; new messages: " + unread(), timeout);
		}
	}

	private Journey accepted() {
		session.connected(Duration.ofSeconds(30));
		reported(connectedPlayer, "has connected");
		connections++;
		return this;
	}

	/**
	 * Waits for a proxy console line after the last one this journey matched and returns it.
	 */
	private String reported(String subject, String event) {
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			var output = proxy.read(console, 500);
			for (var line : output.getLines())
				if (line.getText().contains(subject) && line.getText().contains(event)) {
					console = line.getSequence();
					return line.getText();
				}

			if (Instant.now().isAfter(deadline))
				fail("The proxy did not report \"" + subject + " ... " + event + "\"; console: "
						+ output.getLines().stream().map(line -> line.getText()).toList());
			sleep(Duration.ofMillis(100));
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted during a journey", interrupted);
		}
	}
}
