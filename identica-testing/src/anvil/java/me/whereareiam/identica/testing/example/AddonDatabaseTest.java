package me.whereareiam.identica.testing.example;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The example addon keeps notes in a table of its own in Identica's database, as the developer documentation
 * describes: it installs its schema through the schema installer and writes and reads a row through Dialectica's
 * entity access, from a plain jar that bundles and relocates nothing.
 */
@Tag("operations")
class AddonDatabaseTest {
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true)
	void anAddonInstallsItsSchemaAndReadsBackARow(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY)
				.seesOnConsole("Example addon read note: hello from the addon");
	}
}
