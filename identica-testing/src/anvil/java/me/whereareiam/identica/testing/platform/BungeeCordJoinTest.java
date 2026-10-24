package me.whereareiam.identica.testing.platform;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Platform;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.CLOSED;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * Offline players join Identica on a BungeeCord proxy: they register and log in with Credential, are routed to
 * {@code auth} and on to {@code lobby}, stay where they are when that server refuses them, are disconnected by a
 * lockout and refused at login by a join restriction. BungeeCord verifies premium
 * logins against Mojang's own session server, so no premium player joins here; an offline player with a premium
 * username is still asked for a premium login first.
 */
@Tag("platform")
class BungeeCordJoinTest {
	private static final String PASSWORD = "Secret-123";
	/** Wrong passwords Credential's brute-force sentinel allows by default before it locks the account out. */
	private static final int ALLOWED_ATTEMPTS = 5;
	/** The reason a whitelisted Paper server gives a player who is not on its whitelist. */
	private static final String NOT_WHITELISTED = "You are not whitelisted on this server!";

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL)
	void registersWithCredential(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL)
	void registersWithTheOnlyProviderWhenPickedAutomatically(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL)
	void offersTheRegistrationCommandsInTheClientsCommandTree(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.canRun("pass " + PASSWORD)
				.canRun("passconfirm " + PASSWORD);
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL)
	void logsInWhenReturningAndAgainAfterAReconnect(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL)
	void keepsAPlayerWithAWrongPasswordOnTheStepServer(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login("Wrong-456")
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.remainsOn(AUTH)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL)
	void locksTheAccountOutAfterTheLastAllowedWrongPassword(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);

		Journey alice = Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
		for (int attempt = 1; attempt < ALLOWED_ATTEMPTS; attempt++)
			alice.login("Wrong-456").sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid());

		alice.login("Wrong-456").kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, step = "", complete = "")
	void registersOnTheLobbyWithoutAnyRouting(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(LOBBY)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.remainsOn(LOBBY);
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL, complete = CLOSED,
			configure = SingleAttempt.class)
	void staysOnTheStepServerWhenTheCompletionServerRefusesThem(ScenarioContext anvil) {
		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.seesText(NOT_WHITELISTED)
				.remainsOn(AUTH);
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD, providers = Provider.CREDENTIAL,
			configure = LinkedAllowed.class)
	void refusesANewPlayerAtLoginWhileJoinsAreRestricted(ScenarioContext anvil) {
		Administrator administrator = Administrator.at(anvil, PROXY).restrictsJoins(Provider.CREDENTIAL);
		Journey bob = Journey.offline(anvil, "Bob").attempt()
				.refusedAtLoginWith(m -> m.joinRestriction().getDenied());

		administrator.liftsJoinRestriction(Provider.CREDENTIAL);

		bob.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(platform = Platform.BUNGEECORD)
	void asksAnOfflinePlayerWithAPremiumUsernameForAPremiumLoginFirst(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey alice = Journey.offline(anvil, "Alice").attempt();
		alice.authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	/**
	 * Completion routing as Identica ships it: one attempt, no retry.
	 */
	private static final class SingleAttempt implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.routing().getDefaults().getComplete().setAttempts(RoutingAttemptPolicy.defaultCompletion());
		}
	}

	/**
	 * Configures join restriction for every provider, letting linked accounts through.
	 */
	private static final class LinkedAllowed implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.joinRestriction().setEnabled(true);
			proxy.joinRestriction().setAllow(List.of("linked"));
		}
	}
}
