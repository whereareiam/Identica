package me.whereareiam.identica.testing.verification;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player with a premium account protects it with the built-in authenticator-app (TOTP) verification. Verification
 * is enabled and optional, as Identica generates it: a returning premium player without a method goes straight to the
 * lobby, one who enrolled a method types its code on the step server first.
 */
@Tag("account")
class PremiumVerificationTest {
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = Authenticator.ShortPeriod.class)
	void asksAReturningPremiumPlayerForTheAuthenticatorCodeOnTheStepServer(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey steve = Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());

		Authenticator app = Authenticator.of(steve.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));
		steve.command("2fa enroll confirm " + app.code())
				.sees(m -> m.verification().getCommands().getConfirm().getRecoveryCodes().getBody())
				.command("2fa enroll confirm saved")
				.sees(m -> m.verification().getCommands().getConfirm().getEnabled());

		steve.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.premium().getVerification().getAuthentication().getPrompt())
				.remainsOn(AUTH)
				.command("2fa confirm " + app.nextCode())
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getAuthentication().getBody());
	}
}
