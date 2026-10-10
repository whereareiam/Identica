package me.whereareiam.identica.testing.migration;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * The owner of a premium account moves it to Credential with the provider's own command. Confirming the move
 * never lowers Premium's protection: the rejoin still needs the premium login, and only that login lets the
 * player set a password, which signs them in from then on. An offline client using the premium name is asked to
 * authenticate whether a move is absent, only requested, confirmed or expired. The account exists in the
 * network's stand-in for Mojang, which verifies the premium login.
 */
class PremiumToCredentialMigrationTest {
	private static final String PASSWORD = "Secret-123";
	private static final Duration WINDOW = Duration.ofSeconds(5);

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void movesAPremiumAccountToCredentialAndBack(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.command("credential confirm")
				.kickedWith(m -> m.credential().getCommands().getCredential().getConfirmed())
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getMigration().getSetup().getPrompt())
				.command("pass " + PASSWORD)
				.sees(m -> m.credential().getScenario().getMigration().getSetup().getConfirmPrompt())
				.command("passconfirm " + PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getMigration().getBody())
				.doesNotSee(m -> m.identica().getScenarios().getMigration().getCancelled())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getMigration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void refusesAnOfflineClientUsingThePremiumNameWithoutAPendingMigration(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave();

		Journey impostor = Journey.offline(anvil, "Steve", anvil.definition().getEntrypoint()).attempt();
		impostor.authenticationRequired();
		impostor.attemptAgain().authenticationRequired();
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void refusesAnOfflineClientUsingThePremiumNameWhileTheMigrationIsOnlyRequested(
			ScenarioContext anvil,
			YggdrasilMock yggdrasil
	) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.leave();

		Journey.offline(anvil, "Steve", anvil.definition().getEntrypoint()).attempt()
				.authenticationRequired();
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS)
	void refusesAnOfflineClientUsingThePremiumNameWhileTheMigrationIsConfirmed(
			ScenarioContext anvil,
			YggdrasilMock yggdrasil
	) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey owner = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.command("credential confirm")
				.kickedWith(m -> m.credential().getCommands().getCredential().getConfirmed());

		Journey.offline(anvil, "Steve", anvil.definition().getEntrypoint()).attempt()
				.authenticationRequired();

		owner.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getMigration().getSetup().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = ShortMigrationWindow.class)
	void signsInWithPremiumAgainOnceTheConfirmedMigrationExpired(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.command("credential confirm")
				.kickedWith(m -> m.credential().getCommands().getCredential().getConfirmed())
				.waits(WINDOW.plusSeconds(1))
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody())
				.doesNotSee(m -> m.credential().getScenario().getMigration().getSetup().getPrompt());
	}

	private static final class ShortMigrationWindow implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.engine().getScenarios().getMigration().setPipelineTtl(WINDOW);
		}
	}
}
