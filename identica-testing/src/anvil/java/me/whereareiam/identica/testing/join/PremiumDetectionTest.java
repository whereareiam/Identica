package me.whereareiam.identica.testing.join;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Client;
import me.whereareiam.identica.testing.journey.Journey;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import org.junit.jupiter.api.Tag;
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
@Tag("join")
class PremiumDetectionTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(configure = ClientProfileDetection.class)
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
	@Identica(configure = ClientProfileDetection.class)
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
	@Identica(configure = ClientProfileDetection.class)
	void asksForAPremiumLoginWhenTheClientClaimsThePremiumProfile(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice", offline("Alice"));

		Journey alice = Journey.offline(anvil, "Alice").attempt();
		alice.authenticationRequired();

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(configure = ClientProfileDetection.class)
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
	@Identica(configure = ClientProfileDetection.class)
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
	@Identica(choice = JourneyChoice.PROMPTED, configure = ClientProfileDetection.class)
	void registersAnOfflinePlayerWithAPremiumUsernameWithCredentialWithoutAChoice(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		yggdrasil.register("Alice");

		Journey.offline(anvil, "Alice").join()
				.on(AUTH)
				.doesNotSee(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt());
	}

	@Test
	@Identica(choice = JourneyChoice.PROMPTED, configure = ClientProfileDetection.class)
	void offersPremiumToItsOwnerWhenAsked(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity account = yggdrasil.register("Steve");

		Journey.premium(anvil, account).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"));
	}

	/**
	 * The premium username Steve moves to another account while the lookup cache still holds the old profile. With
	 * {@code recheckOnMismatch}, the new owner's different profile is looked up again and they are offered Premium.
	 */
	@Test
	@Identica(choice = JourneyChoice.PROMPTED, configure = RecheckOnMismatch.class)
	void offersPremiumToTheNewOwnerOfAMovedUsernameWithRecheckOnMismatch(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity newOwner = movedUsername(anvil, yggdrasil);

		Journey.premium(anvil, newOwner).join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody())
				.with(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getDescriptions().get("premium"));
	}

	/**
	 * Lets an offline client look Steve's premium profile up, which caches it, and then moves the username to another
	 * premium account.
	 */
	private static SessionIdentity movedUsername(ScenarioContext anvil, YggdrasilMock yggdrasil) {
		SessionIdentity previousOwner = yggdrasil.register("Steve");
		// From another address, so that the login it leaves pending is not the new owner's to continue.
		Journey.of(anvil, Client.offline("Steve").from("127.0.0.2")).join()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getRegistration().getPrompt())
				.leave();

		yggdrasil.rename(previousOwner.getUniqueId(), "Steve_old");
		return yggdrasil.register("Steve", UUID.randomUUID());
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

	private static final class RecheckOnMismatch implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			new ClientProfileDetection().accept(proxy);
			proxy.premium().getDetection().setRecheckOnMismatch(true);
		}
	}
}
