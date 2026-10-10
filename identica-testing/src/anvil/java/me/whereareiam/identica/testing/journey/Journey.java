package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;
import me.whereareiam.identica.testing.environment.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
	/** Velocity refuses a second login from one address within its default login rate limit of three seconds. */
	private static final Duration LOGIN_RATE_LIMIT = Duration.ofMillis(3200);

	private final String name;
	private final Session session;
	private final Messages messages;
	private final Server server;
	private final ProcessConsole proxy;

	private int read;
	private long console;
	private Instant connectedAt = Instant.EPOCH;
	private int connections;
	private String matched = "";

	private Journey(ScenarioContext anvil, SimulatedPlayer player, String name) {
		this.name = name;
		this.session = player.capability(Session.class);
		this.messages = player.capability(Messages.class);
		this.server = player.capability(Server.class);
		this.proxy = anvil.processes().proxy(IdenticaNetwork.PROXY).console();
		this.console = proxy.checkpoint();
	}

	/**
	 * Creates an offline player that has not connected yet.
	 */
	public static Journey offline(ScenarioContext anvil, String name) {
		return new Journey(anvil, anvil.players().create(name), name);
	}

	/**
	 * Creates a player that signs in with a stored premium account and has not connected yet. The player carries
	 * the account's own username.
	 */
	public static Journey premium(ScenarioContext anvil, AuthenticationAccount account) {
		String name = Objects.requireNonNull(account.getUsername(), "Stored account has no username");
		SimulatedPlayer player = anvil.players().create(PlayerOptions.builder()
				.name(name)
				.authentication(AuthenticationMode.ON_REQUEST)
				.accountId(account.getAccountId())
				.build());
		return new Journey(anvil, player, name);
	}

	/**
	 * Connects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attempt() {
		paceLogin();
		session.connect();
		return this;
	}

	/**
	 * Reconnects without expecting an outcome, for joins the proxy may refuse.
	 */
	public Journey attemptAgain() {
		paceLogin();
		session.rejoin();
		return this;
	}

	/**
	 * Connects and expects the proxy to accept the connection.
	 */
	public Journey join() {
		paceLogin();
		session.connect();
		return accepted();
	}

	/**
	 * Reconnects after a disconnect or kick and expects the proxy to accept the connection.
	 */
	public Journey rejoin() {
		paceLogin();
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
		sleep(SETTLE);
		assertEquals(backend, server.identity().getRoute().getServer());
		List<String> moves = proxy.read(console, 200).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains("[server connection] " + name + " -> ") && line.contains("has connected"))
				.toList();
		assertEquals(List.of(), moves, name + " must stay on " + backend);
		assertTrue(session.state().connected(), name + " must stay connected");
		return this;
	}

	/**
	 * Expects one message, arrived after the previous expectation, that contains every given prompt.
	 */
	public Journey sees(Prompt prompt, Prompt... together) {
		List<Prompt> prompts = new ArrayList<>(List.of(prompt));
		prompts.addAll(List.of(together));
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			List<String> history = messages.history();
			for (int index = read; index < history.size(); index++) {
				String message = history.get(index);
				if (prompts.stream().allMatch(expected -> message.contains(expected.getText()))) {
					read = index + 1;
					matched = message;
					return this;
				}
			}

			if (Instant.now().isAfter(deadline))
				return fail(name + " did not receive " + prompts + "; new messages: " + unread());
			sleep(Duration.ofMillis(100));
		}
	}

	/**
	 * Expects the message matched by the previous {@link #sees} not to contain a prompt.
	 */
	public Journey without(Prompt prompt) {
		assertFalse(matched.contains(prompt.getText()), name + " must not be offered " + prompt + " in: " + matched);
		return this;
	}

	/**
	 * Waits for the first of several alternative messages, arrived after the previous expectation, and returns it.
	 */
	public Prompt seesAnyOf(Prompt... alternatives) {
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			List<String> history = messages.history();
			for (int index = read; index < history.size(); index++)
				for (Prompt alternative : alternatives)
					if (history.get(index).contains(alternative.getText())) {
						read = index + 1;
						return alternative;
					}

			if (Instant.now().isAfter(deadline))
				return fail(name + " did not receive any of " + List.of(alternatives) + "; new messages: " + unread());
			sleep(Duration.ofMillis(100));
		}
	}

	/**
	 * Returns the messages received since the previous expectation, without consuming them.
	 */
	public List<String> unread() {
		List<String> history = messages.history();
		return List.copyOf(history.subList(Math.min(read, history.size()), history.size()));
	}

	/**
	 * Expects that a message has not arrived since the previous expectation, after giving it time to.
	 */
	public Journey doesNotSee(Prompt prompt) {
		sleep(SETTLE);
		List<String> unread = unread();
		assertFalse(unread.stream().anyMatch(line -> line.contains(prompt.getText())),
				name + " must not receive " + prompt + "; new messages: " + unread);
		return this;
	}

	/**
	 * Expects the proxy to drop the connection attempt before the player is in: the client ends up disconnected
	 * and the proxy reports the closed initial connection without ever reporting the player as connected.
	 *
	 * @return the reason the client ended with
	 */
	public String refused() {
		reported("[initial connection] ", "has disconnected");
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (session.state().connected() && Instant.now().isBefore(deadline)) sleep(Duration.ofMillis(100));
		assertFalse(session.state().connected(), name + " must not be connected");
		List<String> accepted = proxy.read(0, 2000).getLines().stream().map(line -> line.getText())
				.filter(line -> line.contains("[connected player] " + name + " (") && line.contains("has connected"))
				.toList();
		assertEquals(connections, accepted.size(), "The proxy must not accept " + name + "; console: " + accepted);
		return String.valueOf(session.state().kickReason());
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
		sees(Prompt.REGISTRATION_CONFIRMATION);
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
	 * Expects the proxy to disconnect the player for a reason: the proxy reports the disconnect and, on the
	 * following console lines, the reason, and the client ends up disconnected. The client's own view of the reason is not asserted,
	 * because a client may see the connection close before it reads the disconnect message.
	 */
	public Journey kickedWith(Prompt reason) {
		reported("[connected player] " + name + " (", "has disconnected");
		reported(reason.getText(), "");
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (session.state().connected() && Instant.now().isBefore(deadline)) sleep(Duration.ofMillis(100));
		assertFalse(session.state().connected(), name + " must be disconnected");
		return this;
	}

	private void paceLogin() {
		Duration wait = Duration.between(Instant.now(), connectedAt.plus(LOGIN_RATE_LIMIT));
		if (!wait.isNegative()) sleep(wait);
		connectedAt = Instant.now();
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
