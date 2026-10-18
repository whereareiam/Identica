package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.capability.messages.MessageWatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.identica.testing.environment.Platform;
import me.whereareiam.identica.testing.environment.Provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
	private final ProxyReports reports;
	private final Session session;
	private final Messages messages;
	/** Messages the player receives from the journey's start; each expectation continues after the previous one. */
	private final MessageWatch chat;
	private final Players proxyPlayers;
	/** The player joining the proxy and the backends it connects the player to, as the proxy reports it. */
	private final PlayerWatch routes;
	/** The player joining each backend, as the backend itself reports it. */
	private final Map<String, PlayerWatch> arrivals;
	private final Map<String, Players> backends;
	/** What the proxy prints from the journey's start on. */
	private final ConsoleLines proxy;
	private final IdenticaMessages configured;

	private String playedAs;
	/** Backend the player was last expected on. */
	private String backend;
	/** Proxy console lines up to and including the one the previous expectation matched. */
	private int console;
	/** Proxy console lines printed before the latest attempt. */
	private int attempted;
	private int connections;
	/** Whether the proxy has named the player of the current connection on its console. */
	private boolean named;
	private String matched = "";
	private MessageText matchedMessage;

	private Journey(ScenarioContext anvil, SimulatedPlayer player, String name, String address, String proxy) {
		this.name = name;
		this.player = player;
		this.source = address != null ? "/" + address + ":" : "";
		this.playedAs = name;
		this.reports = ProxyReports.of(Platform.of(anvil, proxy));
		this.session = player.capability(Session.class);
		this.messages = player.capability(Messages.class);
		this.chat = messages.watch();
		this.proxyPlayers = anvil.processes().proxy(proxy).capability(Players.class);
		this.routes = proxyPlayers.watch();
		this.backends = anvil.processes().servers().stream()
				.collect(Collectors.toUnmodifiableMap(RunningProcess::name, server -> server.capability(Players.class)));
		this.arrivals = backends.entrySet().stream()
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().watch()));
		this.proxy = ConsoleLines.from(anvil.processes().proxy(proxy).console());
		this.configured = new IdenticaMessages(Platform.dataDirectory(anvil, proxy));
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
		attempted = proxy.lines().size();
		named = false;
		session.connect();
		return this;
	}

	/**
	 * Reconnects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attemptAgain() {
		attempted = proxy.lines().size();
		named = false;
		session.rejoin();
		return this;
	}

	/**
	 * Connects and expects the proxy to accept the connection.
	 */
	public Journey join() {
		named = false;
		session.connect();
		return accepted();
	}

	/**
	 * Reconnects after a disconnect or kick and expects the proxy to accept the connection.
	 */
	public Journey rejoin() {
		named = false;
		session.rejoin();
		return accepted();
	}

	/**
	 * Disconnects and expects the proxy to report it.
	 */
	public Journey leave() {
		session.disconnect();
		session.disconnected(TIMEOUT);
		reported(reports.disconnected(playedAs, source), playedAs + " has disconnected");
		return this;
	}

	/**
	 * Expects the proxy to connect the player to a backend: reported by the proxy's agent and on its console, and
	 * observed by that server's agent.
	 */
	public Journey on(String backend) {
		routes.joined(player, backend, TIMEOUT);
		arrival(backend).joined(player, TIMEOUT);
		reported(reports.connected(playedAs, backend), playedAs + " has connected to " + backend);
		this.backend = backend;
		named = true;
		return this;
	}

	/**
	 * Expects the player to stay on a backend for a while, without the proxy connecting it anywhere else. A
	 * connection another server refused, which the proxy reports as connected and then disconnected, is no move.
	 */
	public Journey remainsOn(String backend) {
		routes.neverJoined(player, SETTLE);
		assertEquals(Optional.of(backend), proxyPlayers.backend(player), name + " must stay on " + backend);
		assertTrue(players(backend).profile(player).isPresent(), backend + " must still see " + name);
		List<String> lines = proxy.after(console);
		List<String> moves = new ArrayList<>();
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			String target = reports.connectedTo(line, playedAs);
			if (target == null) continue;

			boolean refused = lines.subList(index + 1, lines.size()).stream()
					.anyMatch(later -> reports.disconnectedFrom(later, playedAs, target));
			if (!refused)
				moves.add(line);
		}
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
			first = player.awaitFirst(TIMEOUT, chat.expectReceived(matcher), session.expectKicked());
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
		return chat.unread();
	}

	/**
	 * Expects that a configured message has not arrived since the previous expectation, after giving it time to.
	 */
	public Journey doesNotSee(Function<IdenticaMessages, ?> message) {
		MessageText unexpected = text(message);
		chat.neverReceived(unexpected::in, SETTLE);
		return this;
	}

	/**
	 * Expects the proxy to demand online authentication that this offline player cannot give: the client ends
	 * disconnected for that cause, and the proxy reports the closed initial connection without ever reporting
	 * this client as connected. Connections of another client with the same username before this journey
	 * started are not counted, and neither are later ones when this client connects from an address of its own.
	 * On a platform that reports neither, the client alone tells.
	 */
	public Journey authenticationRequired() {
		Predicate<String> refused = reports.refused(source);
		if (refused != null)
			reported(refused, "the initial connection of " + name + " has disconnected");
		session.kicked(TIMEOUT);
		assertEquals(DisconnectCause.AUTHENTICATION_REQUIRED, session.state().disconnectCause());
		assertFalse(session.state().connected(), name + " must not be connected");

		Predicate<String> accepted = reports.accepted(playedAs, source);
		if (accepted == null) return this;

		List<String> acceptedLines = proxy.lines().stream()
				.filter(accepted)
				.toList();
		assertEquals(connections, acceptedLines.size(), "The proxy must not accept " + name + "; console: " + acceptedLines);
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
		return this;
	}

	/**
	 * Expects the player's current backend to see the player under a username.
	 */
	public Journey playsAs(String username) {
		assertEquals(username, profile().getUsername(), name + " must play as " + username);
		return this;
	}

	/**
	 * Returns when the proxy connected this player to a backend since the journey began, as the times of its console
	 * lines. A connection the backend refused counts as well, so this tells how often the proxy tried.
	 */
	public List<LocalTime> connectionsTo(String backend) {
		return proxy.lines().stream()
				.filter(reports.connected(playedAs, backend))
				.map(reports::time)
				.toList();
	}

	/**
	 * Returns the profile the backend the player was last expected {@link #on} reports for the player.
	 */
	public PlayerProfile profile() {
		if (backend == null) throw new IllegalStateException(name + " has not been expected on a backend");
		return players(backend).profile(player)
				.orElseThrow(() -> new AssertionError(backend + " does not see " + name));
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
		chat.neverReceived(received -> received.contains(text), SETTLE);
		return this;
	}

	/**
	 * Expects the proxy to refuse the connection before accepting it, with the given message.
	 */
	public Journey refusedWith(Function<IdenticaMessages, ?> message) {
		MessageText reason = text(message);
		Predicate<String> refused = reports.refused(source);
		if (refused != null)
			reported(refused, "the initial connection of " + name + " has disconnected");

		String received = session.kicked(TIMEOUT);
		assertFalse(session.state().connected(), name + " must not be connected");
		assertEquals(DisconnectCause.SERVER, session.state().disconnectCause());
		assertTrue(reason.in(received), name + " was refused with: " + received);
		return this;
	}

	/**
	 * Expects the proxy to disconnect the player for a reason. Velocity reports the disconnect and the reason, on
	 * the same console line for a single-line reason and on the following lines otherwise; BungeeCord reports the
	 * reason first, and nothing about a player it never named. The client receives the same reason, unless the
	 * proxy closed the connection while the client was switching protocol state: such a client only sees the
	 * connection end, so the reason is compared whenever the client attributes the disconnect to the server.
	 */
	public Journey kickedWith(Function<IdenticaMessages, ?> message) {
		MessageText reason = text(message);
		if (reports.namesLogins() || named)
			reportedKick(reason);

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
		List<String> routed = proxy.after(attempted).stream()
				.filter(line -> reports.connectedTo(line, name) != null)
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
		if (reports.namesLogins() || named)
			reported(reports.disconnected(playedAs, source), playedAs + " has disconnected");
		assertFalse(session.state().connected(), name + " must be disconnected");
		return this;
	}

	private void reportedKick(MessageText reason) {
		Predicate<String> disconnected = reports.disconnected(playedAs, source);
		String description = playedAs + " has disconnected";
		if (!reports.reasonFollows()) {
			reported(line -> line.contains(reason.signature()), reason.signature());
			reported(disconnected, description);
			return;
		}

		String disconnect = reported(disconnected, description);
		if (!disconnect.contains(reason.signature()))
			reported(line -> line.contains(reason.signature()), reason.signature());
	}

	private MessageText text(Function<IdenticaMessages, ?> message) {
		return new MessageText(message.apply(configured));
	}

	/**
	 * Waits for a message after the previous expectation and moves this journey's position behind it.
	 */
	private String receive(Predicate<String> matcher, List<MessageText> expected) {
		try {
			return chat.received(matcher, TIMEOUT);
		} catch (IllegalStateException timeout) {
			return fail(name + " did not receive " + expected + "; new messages: " + unread(), timeout);
		}
	}

	private Journey accepted() {
		session.connected(Duration.ofSeconds(30));
		Predicate<String> accepted = reports.accepted(playedAs, source);
		if (accepted != null) {
			reported(accepted, playedAs + " has connected");
			named = true;
		}
		connections++;
		return this;
	}

	/**
	 * Waits for a proxy console line after the last one this journey matched and returns it.
	 *
	 * @param report matches the line
	 * @param description what the line reports, for the failure message
	 */
	private String reported(Predicate<String> report, String description) {
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			List<String> lines = proxy.after(console);
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index);
				if (!report.test(line)) continue;

				console += index + 1;
				return line;
			}

			if (Instant.now().isAfter(deadline))
				fail("The proxy did not report \"" + description + "\"; console: " + lines);
			sleep(Duration.ofMillis(100));
		}
	}

	private Players players(String backend) {
		Players players = backends.get(backend);
		if (players == null) throw new IllegalArgumentException("No running backend is named " + backend);
		return players;
	}

	private PlayerWatch arrival(String backend) {
		PlayerWatch arrival = arrivals.get(backend);
		if (arrival == null) throw new IllegalArgumentException("No running backend is named " + backend);
		return arrival;
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
