package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.testing.environment.IdenticaNetwork;
import me.whereareiam.identica.testing.environment.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * An administrator at one proxy's console. Every command waits for the answer Identica is configured to give,
 * so the next step starts after the proxy has acted.
 */
public final class Administrator {
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final ProcessConsole console;
	private final IdenticaMessages configured;
	private long lastCommand;

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
	 * Asks for a player's session and expects the proxy to describe the one held by the named proxy, and none
	 * held by another.
	 *
	 * @param proxy proxy holding the account's connection
	 */
	public Administrator findsSessionOf(String username, String proxy) {
		runHeldBy("identica admin session info " + username,
				configured.identica().getCommands().getAdmin().getSessions().getStatus().getBody(), proxy);
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
	 * Reserves a UUID for a username, so the account registered under that username gets it.
	 */
	public Administrator reserves(String username, UUID uniqueId) {
		run("identica admin reservation set " + username + " " + uniqueId,
				filled(reservation().getSet(), Map.of("uniqueId", uniqueId.toString())));
		return this;
	}

	/**
	 * Asks for a username's reservation and expects the proxy to name the reserved UUID.
	 */
	public Administrator findsReservation(String username, UUID uniqueId) {
		run("identica admin reservation info " + username,
				filled(reservation().getInfo(), Map.of("uniqueId", uniqueId.toString())));
		return this;
	}

	/**
	 * Asks for a username's reservation and expects the proxy to find none.
	 */
	public Administrator findsNoReservation(String username) {
		run("identica admin reservation info " + username, reservation().getNotFound());
		return this;
	}

	/**
	 * Removes a username's reservation.
	 */
	public Administrator deletesReservation(String username) {
		run("identica admin reservation delete " + username, reservation().getDeleted());
		return this;
	}

	/**
	 * Lists the usernames several accounts share and expects the proxy to name the given one, in lower case.
	 */
	public Administrator findsDuplicateUsername(String username) {
		run("identica admin username duplicates", filled(duplicates().getGroup(), Map.of("username", username)));
		return this;
	}

	/**
	 * Lists the usernames several accounts share and expects the proxy to find none.
	 */
	public Administrator findsNoDuplicateUsernames() {
		run("identica admin username duplicates", duplicates().getEmpty());
		return this;
	}

	/**
	 * Renames an account by hand and expects the proxy to confirm the new username.
	 */
	public Administrator renamesUsername(String target, String username) {
		run("identica admin username rename " + target + " " + username, filled(rename().getSuccess(), Map.of("username", username)));
		return this;
	}

	/**
	 * Tries to rename an account by hand to a username another account holds, and expects the proxy to refuse.
	 */
	public Administrator cannotRenameUsernameTo(String target, String username) {
		run("identica admin username rename " + target + " " + username, filled(rename().getTaken(), Map.of("username", username)));
		return this;
	}

	private Messages.Commands.Admin.Username.Rename rename() {
		return configured.identica().getCommands().getAdmin().getUsername().getRename();
	}

	private Messages.Commands.Admin.Username.Duplicates duplicates() {
		return configured.identica().getCommands().getAdmin().getUsername().getDuplicates();
	}

	private Messages.Commands.Admin.Reservation reservation() {
		return configured.identica().getCommands().getAdmin().getReservation();
	}

	/**
	 * Returns a configured message with some of its placeholders replaced, so the answer has to hold those values.
	 */
	private static String filled(String message, Map<String, String> values) {
		String filled = message;
		for (Map.Entry<String, String> value : values.entrySet())
			filled = filled.replace("{" + value.getKey() + "}", value.getValue());

		return filled;
	}

	/**
	 * Registers a Credential account for a player who has never joined, as an operator moving accounts from another
	 * plugin does.
	 */
	public Administrator registersCredential(String username, String password) {
		run("identica credential register " + username + " " + password, configured.credential().getCommands().getAdmin().getRegistered());
		return this;
	}

	/**
	 * Tries to register a Credential account for a username an account holds already, and expects the proxy to
	 * refuse and name that account.
	 *
	 * @param holder         the username of the account that holds it
	 * @param holderUniqueId the UUID of that account
	 */
	public Administrator cannotRegisterCredential(String username, String password, String holder, UUID holderUniqueId) {
		run("identica credential register " + username + " " + password, filled(
				configured.credential().getCommands().getAdmin().getAccountExists(),
				Map.of("target", username, "username", holder, "uniqueId", holderUniqueId.toString())
		));
		return this;
	}

	/**
	 * Sets a new password for a player's Credential account.
	 */
	public Administrator setsPassword(String username, String password) {
		run("identica credential setpassword " + username + " " + password, configured.credential().getCommands().getAdmin().getPasswordSet());
		return this;
	}

	/**
	 * Restricts new joins through a provider to the players its configured signals allow.
	 */
	public Administrator restrictsJoins(Provider provider) {
		run("identica admin provider restriction join enable " + provider.getId(), joinRestriction().getEnabled());
		return this;
	}

	/**
	 * Tries to restrict joins through a provider whose join restriction is not configured, and expects Identica to
	 * refuse.
	 */
	public Administrator cannotRestrictJoins(Provider provider) {
		run("identica admin provider restriction join enable " + provider.getId(), joinRestriction().getEnableFailed());
		return this;
	}

	/**
	 * Moves a player's account to another provider and expects the move to start. An online player is disconnected
	 * and meets the move when joining again.
	 */
	public Administrator startsMigrationOf(String username, Provider provider) {
		run("identica migration start " + username + " " + provider.getId(),
				filled(migration().getStart().getStarted(), Map.of("target", username, "provider", provider.getId())));
		return this;
	}

	/**
	 * Tries to move a player's account to another provider and expects Identica to find a move already started.
	 */
	public Administrator findsMigrationAlreadyStartedOf(String username, Provider provider) {
		run("identica migration start " + username + " " + provider.getId(),
				filled(migration().getStart().getPendingExists(), Map.of("target", username)));
		return this;
	}

	/**
	 * Cancels a player's started move and expects Identica to confirm it.
	 */
	public Administrator cancelsMigrationOf(String username) {
		run("identica migration cancel " + username, filled(migration().getCancel().getCancelled(), Map.of("target", username)));
		return this;
	}

	/**
	 * Tries to cancel a player's move and expects Identica to find none.
	 */
	public Administrator findsNoMigrationToCancelOf(String username) {
		run("identica migration cancel " + username, filled(migration().getCancel().getNoPending(), Map.of("target", username)));
		return this;
	}

	private Messages.Commands.Migration migration() {
		return configured.identica().getCommands().getMigration();
	}

	/**
	 * Reloads Identica's configuration and expects it to succeed.
	 */
	public Administrator reloads() {
		run("identica admin reload", configured.identica().getCommands().getReload().getSuccess());
		return this;
	}

	/**
	 * Reloads Identica's configuration and expects it to fail with the configured error answer.
	 */
	public Administrator failsToReload() {
		run("identica admin reload", configured.identica().getCommands().getReload().getError());
		return this;
	}

	/**
	 * Expects the console to show a line containing every text since the previous command was sent, or since the
	 * proxy started before the first one, such as what an addon logs when it starts or reacts to a command.
	 */
	public Administrator seesOnConsole(String... texts) {
		List<String> expected = List.of(texts);
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (true) {
			List<String> lines = console.read(lastCommand, 2000).getLines().stream().map(line -> line.getText()).toList();
			if (lines.stream().anyMatch(line -> expected.stream().allMatch(line::contains))) return this;
			if (Instant.now().isAfter(deadline))
				fail("The console did not show a line with " + expected + "; console: " + lines);

			pause(100);
		}
	}

	/**
	 * Lifts the join restriction of a provider.
	 */
	public Administrator liftsJoinRestriction(Provider provider) {
		run("identica admin provider restriction join disable " + provider.getId(), joinRestriction().getDisabled());
		return this;
	}

	private JoinRestrictionMessages.Commands joinRestriction() {
		return configured.joinRestriction().getCommands();
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
		lastCommand = before;
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
