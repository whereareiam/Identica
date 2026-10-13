package me.whereareiam.identica.testing.verification;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.fixture.Authenticator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A player with a Credential account protects it with the built-in authenticator-app (TOTP) verification on a
 * network whose only provider is Credential. Verification is enabled and optional, as Identica generates it: a
 * player who enrolled a method is asked for its code after the password, everyone else only types the password.
 * Each authenticator code is accepted once, so a journey that types a second code takes the app's next one.
 */
class TotpVerificationTest {
	private static final String PASSWORD = "Secret-123";
	/** Recovery codes Identica generates by default. */
	private static final int RECOVERY_CODES = 8;

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void asksForTheAuthenticatorCodeAfterThePasswordOnceEnrolled(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		alice.journey().leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.remainsOn(AUTH)
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void refusesAWrongAuthenticatorCodeAndAcceptsTheRightOne(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		alice.journey().leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + alice.app().wrongCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.doesNotSee(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.remainsOn(AUTH)
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * A code typed while Alice is still asked for her password has nothing to confirm; her password prompt is not
	 * shown again, and the login goes on as usual.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void answersACodeTypedAtThePasswordPromptWithNothingPending(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		alice.journey().leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.command("2fa confirm " + alice.app().wrongCode())
				.sees(m -> m.verification().getCommands().getConfirm().getNoPending())
				.doesNotSee(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void acceptsARecoveryCodeInsteadOfTheAuthenticatorCodeOnlyOnce(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);
		String recoveryCode = alice.recoveryCodes().getFirst();

		alice.journey().leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + recoveryCode)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.command("2fa confirm " + recoveryCode)
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void logsInWithThePasswordAloneOnceTheAuthenticatorIsDisabled(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		alice.journey().command("2fa disable totp")
				.sees(m -> m.verification().getCommands().getDisable().getProtectedPrompt())
				.command("2fa confirm " + alice.app().nextCode())
				.sees(m -> m.verification().getCommands().getDisable().getDisabled())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void keepsTheAuthenticatorWhenTheCodeConfirmingItsDisablingIsWrong(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		alice.journey().command("2fa disable totp")
				.sees(m -> m.verification().getCommands().getDisable().getProtectedPrompt())
				.command("2fa confirm " + alice.app().wrongCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt())
				.remainsOn(AUTH);
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void doesNotEnrollTheAuthenticatorWhenTheConfirmingCodeIsWrong(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
		Authenticator app = Authenticator.of(alice.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));

		alice.command("2fa enroll confirm " + app.wrongCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void refusesAnAuthenticatorCodeUsedAtAnEarlierLoginAndAcceptsTheNextOne(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		verificationPrompt(alice.journey().leave())
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
		verificationPrompt(alice.journey().leave())
				.command("2fa confirm " + alice.app().usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH)
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void refusesTheCodeThatConfirmedTheEnrollmentAtTheFirstLogin(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		verificationPrompt(alice.journey().leave())
				.command("2fa confirm " + alice.app().usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH)
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Alice's phone runs a period ahead, so she types the code of the next period. Identica accepts it as clock
	 * drift, and from then on only codes after it.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void acceptsTheCodeOfAnAppWhoseClockRunsAheadOnce(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		verificationPrompt(alice.journey().leave())
				.command("2fa confirm " + alice.app().codeAhead())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
		verificationPrompt(alice.journey().leave())
				.command("2fa confirm " + alice.app().usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH)
				.command("2fa confirm " + alice.app().nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void keepsTheAuthenticatorWhenAUsedCodeConfirmsItsDisabling(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);

		verificationPrompt(alice.journey().command("2fa disable totp")
				.sees(m -> m.verification().getCommands().getDisable().getProtectedPrompt())
				.command("2fa confirm " + alice.app().usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.leave())
				.remainsOn(AUTH);
	}

	/**
	 * A new authenticator starts from the code that confirmed it, as the first one did.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = Authenticator.ShortPeriod.class)
	void refusesTheCodeThatConfirmedAReEnrolledAuthenticator(ScenarioContext anvil) {
		Enrolled alice = enrolled(anvil);
		Journey journey = alice.journey().command("2fa disable totp")
				.sees(m -> m.verification().getCommands().getDisable().getProtectedPrompt())
				.command("2fa confirm " + alice.app().nextCode())
				.sees(m -> m.verification().getCommands().getDisable().getDisabled());

		Authenticator phone = Authenticator.of(journey.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));
		journey.command("2fa enroll confirm " + phone.code())
				.sees(m -> m.verification().getCommands().getConfirm().getRecoveryCodes().getBody())
				.command("2fa enroll confirm saved")
				.sees(m -> m.verification().getCommands().getConfirm().getEnabled());

		verificationPrompt(journey.leave())
				.command("2fa confirm " + phone.usedCode())
				.sees(m -> m.verification().getCommands().getConfirm().getInvalidCode())
				.remainsOn(AUTH)
				.command("2fa confirm " + phone.nextCode())
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = RequiredVerification.class)
	void refusesALoginWithoutAMethodWhileVerificationIsRequired(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getVerification().getRequired());
	}

	/**
	 * Registers Alice, logs her in and enrolls an authenticator app: she reads the secret, confirms it with the
	 * app's code, keeps the recovery codes and confirms she saved them. Alice ends on the lobby, logged in.
	 */
	private static Enrolled enrolled(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);

		Authenticator app = Authenticator.of(alice.command("2fa enroll totp")
				.sees(m -> m.verification().getMethods().getTotp().getPending())
				.reads("secret"));
		List<String> recoveryCodes = Arrays.asList(alice.command("2fa enroll confirm " + app.code())
				.sees(m -> m.verification().getCommands().getConfirm().getRecoveryCodes().getBody())
				.reads("entries")
				.split("\\s+"));
		assertEquals(RECOVERY_CODES, recoveryCodes.size(), "Alice must be shown every recovery code: " + recoveryCodes);

		alice.command("2fa enroll confirm saved")
				.sees(m -> m.verification().getCommands().getConfirm().getEnabled())
				.sees(m -> m.verification().getCommands().getConfirm().getAutoSelected());
		return new Enrolled(alice, app, recoveryCodes);
	}

	/**
	 * Rejoins after leaving and types the password, which brings Alice to the authenticator prompt.
	 */
	private static Journey verificationPrompt(Journey left) {
		return left.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getVerification().getPrompt());
	}

	private record Enrolled(Journey journey, Authenticator app, List<String> recoveryCodes) {
	}

	private static final class RequiredVerification implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.verification().getDefaults().setRequired(true);
		}
	}
}
