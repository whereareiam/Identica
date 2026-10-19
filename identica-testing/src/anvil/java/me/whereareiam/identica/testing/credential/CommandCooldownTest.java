package me.whereareiam.identica.testing.credential;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.fixture.Accounts;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;

/**
 * A player runs Credential's commands faster than their cooldowns allow: a command used again within its cooldown is
 * refused with the cooldown message and does nothing, until the cooldown is over. By default each command has a
 * cooldown of its own of two seconds; commands an operator puts into one group share it.
 */
@Tag("account")
class CommandCooldownTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void refusesALoginRepeatedWithinItsCooldown(ScenarioContext anvil) {
		returning(anvil)
				.login("Wrong-789")
				.sees(m -> m.credential().getScenario().getAuthentication().getStatus().getInvalid())
				.login(PASSWORD)
				.sees(m -> m.identica().getCommands().getExceptions().getCooldown())
				.remainsOn(AUTH)
				.waitsForCooldown()
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void letsAnotherCommandRunRightAfter(ScenarioContext anvil) {
		returning(anvil)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getAlreadyPrimary());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = SharedGroup.class)
	void sharesTheCooldownOfAGroup(ScenarioContext anvil) {
		returning(anvil)
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody())
				.command("credential")
				.sees(m -> m.identica().getCommands().getExceptions().getCooldown())
				.waitsForCooldown()
				.command("credential")
				.sees(m -> m.credential().getCommands().getCredential().getAlreadyPrimary());
	}

	private static Journey returning(ScenarioContext anvil) {
		return Accounts.credential(anvil, "Alice", PASSWORD).rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	/**
	 * Configured as an operator puts {@code /login} and {@code /credential} into one cooldown group.
	 */
	private static final class SharedGroup implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			CommandDefinition.Cooldown shared = CommandDefinition.Cooldown.builder()
					.enabled(true)
					.duration((int) Journey.COMMAND_COOLDOWN.toSeconds())
					.group("credential")
					.build();
			proxy.credentialCommands().getCommands().get("login").setCooldown(shared);
			proxy.credentialCommands().getCommands().get("credential").setCooldown(shared);
		}
	}
}
