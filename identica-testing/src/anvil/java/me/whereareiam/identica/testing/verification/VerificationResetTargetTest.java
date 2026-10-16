package me.whereareiam.identica.testing.verification;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Administrator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * An administrator resets the two-step verification of an account that does not exist.
 */
@Tag("account")
class VerificationResetTargetTest {
	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void reportsAnUnknownUsername(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY).findsNoVerificationToResetOf("Nobody");
	}

	@Test
	@Identica(providers = Provider.CREDENTIAL)
	void reportsAnUnknownUniqueId(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY).findsNoVerificationToResetOf(UUID.randomUUID().toString());
	}
}
