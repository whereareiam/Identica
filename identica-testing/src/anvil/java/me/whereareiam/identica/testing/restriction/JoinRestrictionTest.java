package me.whereareiam.identica.testing.restriction;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
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
 * An administrator restricts new joins through Credential, the network's only provider. A player with one of the
 * signals the join restriction allows still gets through, and a provider whose join restriction is not configured
 * cannot be restricted.
 */
@Tag("join")
class JoinRestrictionTest {
	private static final String PASSWORD = "Secret-123";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = LinkedAllowed.class)
	void letsALinkedAccountLogInWhileRestricted(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD);
		Administrator.at(anvil, PROXY).restrictsJoins(Provider.CREDENTIAL);

		alice.rejoin()
				.on(AUTH)
				.sees(m -> m.credential().getScenario().getAuthentication().getPrompt())
				.login(PASSWORD)
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getAuthentication().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, configure = RecognizedAllowed.class)
	void letsARecognizedPlayerInWithoutThePasswordWhileRestricted(ScenarioContext anvil) {
		Journey alice = Accounts.credential(anvil, "Alice", PASSWORD);
		Administrator.at(anvil, PROXY).restrictsJoins(Provider.CREDENTIAL);

		alice.rejoin()
				.on(LOBBY)
				.sees(m -> m.credential().getCompletion().getRecognition().getBody());
	}

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL)
	void refusesToRestrictAProviderWhoseJoinRestrictionIsNotConfigured(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY).cannotRestrictJoins(Provider.CREDENTIAL);

		Journey.offline(anvil, "Bob").join()
				.on(AUTH)
				.sees(m -> m.identica().getEngine().getJourney().getStep().getEnrollment().getBody());
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

	/**
	 * Lets recognized players through a restriction. Recognition is on for a network whose players all connect
	 * from the loopback address.
	 */
	private static final class RecognizedAllowed implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.joinRestriction().setEnabled(true);
			proxy.joinRestriction().setAllow(List.of("recognized"));

			RecognitionSettings recognition = proxy.recognition();
			recognition.setEnabled(true);
			recognition.getEligibility().getUntrustedIps().setEnabled(false);
		}
	}
}
