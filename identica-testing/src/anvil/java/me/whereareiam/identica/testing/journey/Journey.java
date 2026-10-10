package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.AuthenticationMode;
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
	private final Session session;
	private final Messages messages;
	private final Server server;
	private final ProcessConsole proxy;
	private final IdenticaMessages configured;

	private int read;
	private long console;
	private int connections;
	private String matched = "";

	private Journey(ScenarioContext anvil, SimulatedPlayer player, String name) {
		this.name = name;
		this.session = player.capability(Session.class);
		this.messages = player.capability(Messages.class);
		this.server = player.capability(Server.class);
		this.proxy = anvil.processes().proxy(IdenticaNetwork.PROXY).console();
		this.configured = new IdenticaMessages(anvil.processes().proxy(IdenticaNetwork.PROXY).workDirectory().resolve(IdenticaNetwork.DATA));
		this.console = proxy.checkpoint();
		this.read = messages.checkpoint();
	}

	/**
	 * Creates an offline player that has not connected yet.
	 */
	public static Journey offline(ScenarioContext anvil, String name) {
		return new Journey(anvil, anvil.players().create(name), name);
	}

	/**
	 * Creates a player that owns a premium account registered with the local Mojang service and has not connected
	 * yet. It authenticates when the proxy asks for it, as a premium client does.
	 */
	public static Journey premium(ScenarioContext anvil, SessionIdentity account) {
		SimulatedPlayer player = anvil.players().create(PlayerOptions.builder()
				.name(account.getUsername())
				.authentication(AuthenticationMode.ON_REQUEST)
				.sessionIdentity(account)
				.build());
		return new Journey(anvil, player, account.getUsername());
	}

	/**
	 * Connects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attempt() {
		session.connect();
		return this;
	}

	/**
	 * Reconnects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attemptAgain() {
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
		reported("[connected player] " + name + " (", "has disconnected");
		return this;
	}

	/**
	 * Expects the player on a backend: observed by that server's agent and reported by the proxy.
	 */
	public Journey on(String backend) {
		PlayerIdentity identity = server.joined(backend, TIMEOUT);
		assertEquals(backend, identity.getRoute().getServer());
		reported("[server connection] " + name + " -> " + backend, "has connected");
		return this;
	}

	/**
	 * Expects the player to stay on a backend for a while, without the proxy connecting it anywhere else.
	 */
	public Journey remainsOn(String backend) {
		server.stayed(backend, SETTLE);
		List<String> moves = proxy.read(console, 200).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains("[server connection] " + name + " -> ") && line.contains("has connected"))
				.toList();
		assertEquals(List.of(), moves, name + " must stay on " + backend);
		assertTrue(session.state().connected(), name + " must stay connected");
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
		return this;
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
	 * the player as connected.
	 */
	public Journey authenticationRequired() {
		reported("[initial connection] ", "has disconnected");
		session.kicked(TIMEOUT);
		assertEquals(DisconnectCause.AUTHENTICATION_REQUIRED, session.state().disconnectCause());
		assertFalse(session.state().connected(), name + " must not be connected");
		List<String> accepted = proxy.read(0, 2000).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains("[connected player] " + name + " (") && line.contains("has connected"))
				.toList();
		assertEquals(connections, accepted.size(), "The proxy must not accept " + name + "; console: " + accepted);
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
	 * Expects the proxy to disconnect the player for a reason. The proxy reports the disconnect and, on the
	 * following console lines, the reason. The client receives the same reason, unless the proxy closed the
	 * connection while the client was switching protocol state: such a client only sees the connection end, so
	 * the reason is compared whenever the client attributes the disconnect to the server.
	 */
	public Journey kickedWith(Function<IdenticaMessages, ?> message) {
		MessageText reason = text(message);
		reported("[connected player] " + name + " (", "has disconnected");
		reported(reason.signature(), "");

		String received = session.kicked(TIMEOUT);
		assertFalse(session.state().connected(), name + " must be disconnected");
		if (session.state().disconnectCause() == DisconnectCause.SERVER)
			assertTrue(reason.in(received), name + " was disconnected with: " + received);
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
		reported("[connected player] " + name + " (", "has connected");
		connections++;
		return this;
	}

	/**
	 * Waits for a proxy console line after the last one this journey matched.
	 */
	private void reported(String subject, String event) {
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			var output = proxy.read(console, 500);
			for (var line : output.getLines())
				if (line.getText().contains(subject) && line.getText().contains(event)) {
					console = line.getSequence();
					return;
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
