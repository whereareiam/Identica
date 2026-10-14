package me.whereareiam.identica.testing.credential;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * A player with a Credential account manages it on a network whose only provider is Credential: they change their
 * password with {@code /changepassword} once logged in, or an administrator sets a new one from the console.
 * Password rules are Credential's defaults; unless a test says otherwise, the new password has to be repeated and
 * the command's usage offers the repeat argument.
 */
@Tag("account")
class PasswordChangeTest {
	private static final String PASSWORD = "Secret-123";
	private static final String NEW_PASSWORD = "Changed-456";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void asksForTheNewPasswordOnceItIsChanged(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword " + PASSWORD + " " + NEW_PASSWORD + " " + NEW_PASSWORD)
				.sees(m -> m.credential().getChangePassword().getSuccess())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.remainsOn(AUTH)
				.login(NEW_PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void keepsThePasswordWhenTheCurrentOneIsWrong(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword Wrong-789 " + NEW_PASSWORD + " " + NEW_PASSWORD)
				.sees(m -> m.credential().getChangePassword().getInvalidCurrent())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void refusesANewPasswordThatDiffersFromItsRepetition(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword " + PASSWORD + " " + NEW_PASSWORD + " Other-789")
				.sees(m -> m.credential().getChangePassword().getMismatch());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void offersTheRepeatArgumentInTheSyntaxWhileTheRepetitionIsRequired(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword " + PASSWORD)
				.sees(m -> m.identica().getCommands().getExceptions().getInvalidSyntax())
				.with(m -> m.identica().getCommands().getHelp().getArgumentFormat().getOptionalArgument());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = WithoutRepeat.class)
	void leavesTheRepeatArgumentOutOfTheSyntaxOnceTheUsageDropsIt(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword " + PASSWORD)
				.sees(m -> m.identica().getCommands().getExceptions().getInvalidSyntax())
				.without(m -> m.identica().getCommands().getHelp().getArgumentFormat().getOptionalArgument());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = WithoutRepeat.class)
	void changesThePasswordWithoutARepetitionWhenItIsNotRequired(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword " + PASSWORD + " " + NEW_PASSWORD)
				.sees(m -> m.credential().getChangePassword().getSuccess())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(NEW_PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void refusesANewPasswordThePasswordRulesReject(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("changepassword " + PASSWORD + " Ab1 Ab1")
				.sees(m -> m.credential().getPassword().getTooShort());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void logsInWithThePasswordAnAdministratorSet(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD);
		Administrator.at(anvil, PROXY).setsPassword("Alice", NEW_PASSWORD);

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.login(NEW_PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void answersThatCredentialIsAlreadyThePrimaryProvider(ScenarioContext anvil) {
		loggedIn(anvil)
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getAlreadyPrimary());
	}

	private static Journey loggedIn(ScenarioContext anvil) {
		return Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	/**
	 * Configured as an operator turns the repetition off: the setting no longer requires it, and the
	 * {@code change-password} usage no longer offers the argument.
	 */
	private static final class WithoutRepeat implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.credential().getScenario().getChangePassword().setRequireRepeat(false);
			proxy.credentialCommands().getCommands().get("change-password").setUsage("{alias} <current> <new>");
		}
	}
}
