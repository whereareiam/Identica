package me.whereareiam.identica.testing.migration;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * The owner of a premium account asks to move the account to the other provider and confirms only after
 * {@code commands.behavior.migration.confirmTtl}. The player is told the request expired, and the account stays
 * where it was.
 */
@Tag("operations")
class ProviderMoveExpiryTest {
	private static final String PASSWORD = "Secret-123";
	private static final Duration CONFIRM_TTL = Duration.ofSeconds(2);

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, configure = ShortConfirmation.class)
	void aMoveToPremiumConfirmedTooLateExpires(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		registeredWithCredential(anvil, yggdrasil)
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.waits(CONFIRM_TTL)
				.command("premium confirm")
				.sees(m -> m.premium().getCommands().getPremium().getExpired())
				.command("premium confirm")
				.sees(m -> m.premium().getCommands().getPremium().getNoPending())
				.remainsOn(LOBBY)
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, configure = ShortConfirmation.class)
	void aMoveBackToCredentialConfirmedTooLateExpires(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		registeredWithCredential(anvil, yggdrasil)
				.command("premium")
				.sees(m -> m.premium().getCommands().getPremium().getConfirm())
				.command("premium confirm")
				.kickedWith(m -> m.premium().getCommands().getPremium().getConfirmed())
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getMigration().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getConfirm())
				.waits(CONFIRM_TTL)
				.command("credential confirm")
				.sees(m -> m.credential().getCommands().getCredential().getExpired())
				.remainsOn(LOBBY)
				.leave()
				.rejoin()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}

	private static Journey registeredWithCredential(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		return Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.enroll(Provider.CREDENTIAL)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody());
	}

	static final class ShortConfirmation implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.commands().getBehavior().getMigration().setConfirmTtl(CONFIRM_TTL);
		}
	}
}
