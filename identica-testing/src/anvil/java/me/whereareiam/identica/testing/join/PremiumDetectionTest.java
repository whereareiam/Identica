package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.CLIENT_PROFILE;
import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.LOOKUP;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.AUTH;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.LOBBY;
import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * Premium compares the UUID a client claims at login with the premium profile of its username before it asks for a
 * premium login. An offline player with a premium username is therefore not refused first, and the owner of a
 * premium account still joins without a prompt.
 */
class PremiumDetectionTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = ClientProfileDetection.class)
	void registersAPremiumUsernameWithCredentialOnTheFirstJoin(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.register(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRegistration().getBody())
				.leave()
				.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = ClientProfileDetection.class)
	void logsThePremiumOwnerInWithoutAnyPrompt(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody())
				.leave()
				.rejoin()
				.on(LOBBY);
	}

	/**
	 * A client that sends the premium profile id of its username is asked for a premium login. An offline client that
	 * does so is refused once and falls back to Credential on its next join, as it does without the comparison.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = ClientProfileDetection.class)
	void asksForAPremiumLoginWhenTheClientClaimsThePremiumProfile(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice", offline("Alice"));

		Journey alice = Journey.offline(anvil, "Alice").attempt();
		alice.authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = ClientProfileDetection.class)
	void refusesAnotherClientWithTheUsernameOfALinkedPremiumAccount(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.leave();

		// A second client with the same username, so Anvil names it after the proxy it joins through.
		Journey.offline(anvil, "Steve", PROXY).attempt()
				.refusedWith(m -> m.premium().getVerification().getOwnedUsername());
	}

	/**
	 * An offline client leaves a Credential registration pending; the owner of the premium account, joining from the
	 * same address, registers with Premium instead of continuing that registration.
	 */
	@Test
	@Identica(mode = JourneyMode.SEAMLESS, configure = ClientProfileDetection.class)
	void registersTheOwnerAfterAnOfflineClientLeftARegistration(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");
		// A second client with the same username, so Anvil names it after the proxy it joins through.
		Journey.offline(anvil, "Steve", PROXY).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.leave();

		Journey.premium(anvil, account).join()
				.on(LOBBY)
				.sees(m -> m.premium().getCompletion().getRegistration().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, configure = ClientProfileDetection.class)
	void offersOnlyCredentialToAnOfflinePlayerWithAPremiumUsername(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("credential"))
				.without(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"));
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, configure = ClientProfileDetection.class)
	void offersPremiumToItsOwnerInInteractiveMode(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"));
	}

	private static UUID offline(String username) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
	}

	private static final class ClientProfileDetection implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.premium().getDetection().setMethods(List.of(CLIENT_PROFILE, LOOKUP));
		}
	}
}
