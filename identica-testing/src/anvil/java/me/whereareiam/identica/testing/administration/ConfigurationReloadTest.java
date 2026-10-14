package me.whereareiam.identica.testing.administration;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator edits a configuration file of the running proxy and reloads Identica with
 * {@code /identica admin reload}. A file that reads correctly takes effect for the next players; a file Identica cannot
 * read is reported and the configuration in use stays as it was. Credential is the network's only provider.
 */
@Tag("operations")
class ConfigurationReloadTest {
	private static final String PASSWORD = "Secret-123";
	private static final String WRONG = "Wrong-456";
	private static final String MESSAGES = "messages.yml";
	private static final String ENGINE = "engine.yml";
	private static final String CREDENTIAL_MESSAGES = "providers/Credential/messages.yml";
	private static final String CREDENTIAL_SETTINGS = "providers/Credential/settings.yml";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void showsAChangedCoreMessageAfterAReload(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, MESSAGES, Messages.class, messages ->
				messages.getEngine().getJourney().getStep().getEnrollment().setBody(List.of("<white>Choose how you sign in:</white>")));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void appliesAChangedCoreSettingAfterAReload(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, ENGINE, Engine.class, engine ->
				engine.getScenarios().getRegistration().setAutoSelectSingleProvider(true));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, providers = Provider.CREDENTIAL)
	void showsAChangedProviderMessageAfterAReload(ScenarioContext anvil) {
		ProxyConfiguration.editGenerated(anvil, CREDENTIAL_MESSAGES, CredentialMessages.class, messages ->
				messages.getScenario().getRegistration().setPrompt(List.of("<white>Pick a password and type it after /pass.</white>")));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void appliesAChangedProviderSettingAfterAReload(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);
		ProxyConfiguration.editGenerated(anvil, CREDENTIAL_SETTINGS, CredentialSettings.class, settings ->
				settings.getScenario().getAuthentication().getBruteforce().setMaxAttempts(2));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(WRONG)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.login(WRONG)
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void appliesChangesWhilePlayersAreOnline(ScenarioContext anvil) {
		Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY);
		Accounts.credentialByAdmin(anvil, "Bob", PASSWORD);

		ProxyConfiguration.editGenerated(anvil, CREDENTIAL_MESSAGES, CredentialMessages.class, messages ->
				messages.getScenario().getAuthentication().setPrompt(List.of("<white>Welcome back, sign in with /login.</white>")));
		ProxyConfiguration.editGenerated(anvil, CREDENTIAL_SETTINGS, CredentialSettings.class, settings ->
				settings.getScenario().getAuthentication().getBruteforce().setMaxAttempts(2));
		Administrator.at(anvil, PROXY).reloads();

		Journey.offline(anvil, "Bob").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(WRONG)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.login(WRONG)
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = TwoAttempts.class)
	void keepsTheSettingsInUseWhenTheChangedFileCannotBeRead(ScenarioContext anvil) {
		Accounts.credentialByAdmin(anvil, "Alice", PASSWORD);
		ProxyConfiguration.corruptGenerated(anvil, CREDENTIAL_SETTINGS, "scenario: [\n");
		Administrator.at(anvil, PROXY).failsToReload();

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(WRONG)
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.login(WRONG)
				.kickedWith(m -> m.credential().getScenario().getAuthentication().getBruteforce().getExceeded());
	}

	/**
	 * Locks an account out after two wrong passwords instead of Credential's default five, so the lockout shows
	 * which settings are in use.
	 */
	private static final class TwoAttempts implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.credential().getScenario().getAuthentication().getBruteforce().setMaxAttempts(2);
		}
	}
}
