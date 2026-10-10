package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

			try {
				Thread.sleep(100);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for the console", interrupted);
			}
		}
	}
}
