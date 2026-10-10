package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * An administrator at one proxy's console. Every command waits for the answer Identica is configured to give,
 * so the next step starts after the proxy has acted.
 */
public final class Administrator {
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final ProcessConsole console;
	private final IdenticaMessages configured;

	private Administrator(ScenarioContext anvil, String proxy) {
		this.console = anvil.processes().proxy(proxy).console();
		this.configured = new IdenticaMessages(anvil.processes().proxy(proxy).workDirectory().resolve(IdenticaNetwork.DATA));
	}

	/**
	 * Returns the administrator of one proxy.
	 */
	public static Administrator at(ScenarioContext anvil, String proxy) {
		return new Administrator(anvil, proxy);
	}

	/**
	 * Deletes an account and confirms the deletion.
	 */
	public Administrator deletes(String username) {
		Messages.Commands.Admin.Delete delete = configured.identica().getCommands().getAdmin().getDelete();
		run("identica admin delete " + username, delete.getConfirm());
		run("identica admin delete confirm", delete.getSuccess());
		return this;
	}

	/**
	 * Ends the session of an online player.
	 */
	public Administrator endsSessionOf(String username) {
		run("identica admin session end " + username, configured.identica().getCommands().getAdmin().getSessions().getEnd().getEnded());
		return this;
	}

	/**
	 * Asks for a player's session and expects the proxy to describe one.
	 */
	public Administrator findsSessionOf(String username) {
		run("identica admin session info " + username, configured.identica().getCommands().getAdmin().getSessions().getStatus().getBody());
		return this;
	}

	/**
	 * Asks for a player's session and expects the proxy to find none.
	 */
	public Administrator findsNoSessionOf(String username) {
		run("identica admin session info " + username, configured.identica().getCommands().getAdmin().getSessions().getStatus().getNotFound());
		return this;
	}

	/**
	 * Asks for a player's sessions and expects the proxy to describe exactly one for each named proxy: the
	 * sessions of the connections the account is online through.
	 *
	 * @param proxies proxies holding a connection of the account
	 */
	public Administrator findsSessionsOf(String username, String... proxies) {
		runHeldBy("identica admin session info " + username,
				configured.identica().getCommands().getAdmin().getSessions().getStatus().getBody(), proxies);
		return this;
	}

	/**
	 * Lists the sessions and expects exactly the ones held by the named proxies.
	 */
	public Administrator listsSessionsHeldBy(String... proxies) {
		runHeldBy("identica admin session list",
				configured.identica().getCommands().getAdmin().getSessions().getListing().getEntry().getFormat(), proxies);
		return this;
	}

	/**
	 * Clears an account's provider data and confirms it.
	 */
	public Administrator clears(String username) {
		Messages.Commands.Admin.Clear clear = configured.identica().getCommands().getAdmin().getClear();
		run("identica admin clear " + username, clear.getConfirm());
		run("identica admin clear confirm", clear.getSuccess());
		return this;
	}

	/**
	 * Sends a command until its answer shows a message once for each named proxy and for no other proxy of the
	 * cluster. The message names the proxy holding a session through its {@code {server}} placeholder. The
	 * command is repeated because a session ends a moment after its player's disconnect is reported.
	 */
	private void runHeldBy(String command, Object message, String... proxies) {
		Set<String> holding = Set.of(proxies);
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			long before = console.checkpoint();
			console.sendCommand(command);
			pause(500);

			String answer = String.join("\n", console.read(before, 500).getLines().stream().map(line -> line.getText()).toList());
			boolean exact = true;
			for (String proxy : List.of(IdenticaNetwork.PROXY_A, IdenticaNetwork.PROXY_B))
				exact &= heldBy(message, proxy).in(answer) == holding.contains(proxy);
			if (exact) return;
			if (Instant.now().isAfter(deadline))
				fail("The proxy did not answer \"" + command + "\" with sessions held by exactly " + holding + "; console: " + answer);
		}
	}

	/**
	 * Returns a message as it reads for a session held by one proxy, line by line as a console prints it.
	 */
	private static MessageText heldBy(Object message, String proxy) {
		List<String> lines = new ArrayList<>();
		for (Object line : message instanceof List<?> list ? list : List.of(message))
			lines.addAll(List.of(String.valueOf(line).replace("{server}", proxy).split("\n")));

		return new MessageText(lines);
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for the console", interrupted);
		}
	}

	/**
	 * Sends a command and waits until the console shows the configured answer, whatever its placeholders hold.
	 */
	private void run(String command, Object answer) {
		MessageText expected = new MessageText(answer);
		long before = console.checkpoint();
		console.sendCommand(command);

		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			List<String> lines = console.read(before, 500).getLines().stream().map(line -> line.getText()).toList();
			if (expected.in(String.join("\n", lines))) return;
			if (Instant.now().isAfter(deadline))
				fail("The proxy did not answer \"" + command + "\" with " + expected + "; console: " + lines);

			pause(100);
		}
	}
}
