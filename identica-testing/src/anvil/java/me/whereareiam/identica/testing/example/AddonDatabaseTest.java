package me.whereareiam.identica.testing.example;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.testing.environment.Identica;
import me.whereareiam.identica.testing.environment.Provider;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * The example addon keeps notes in a table of its own in Identica's database, as the developer documentation
 * describes: it installs its schema through the schema installer and writes and reads a row through Dialectica's
 * entity access, from a plain jar that bundles and relocates nothing.
 */
@Tag("operations")
class AddonDatabaseTest {
	/** Named to load before Identica, as Velocity loads plugins without dependencies alphabetically. */
	private static final String CLASHING_JAR = "aaa-clashing.jar";

	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true)
	void anAddonInstallsItsSchemaAndReadsBackARow(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY)
				.seesOnConsole("Example addon read note: hello from the addon");
	}

	/**
	 * Another plugin bundles an earlier Dialectica without relocating it, and the addon's classes see that copy
	 * instead of Identica's. Identica refuses the addon's schema and names both jars.
	 */
	@Test
	@Identica(mode = JourneyMode.INTERACTIVE, providers = Provider.CREDENTIAL, addon = true, configure = ClashingPlugin.class)
	void namesAPluginThatBundlesAnotherDialectica(ScenarioContext anvil) {
		Administrator.at(anvil, PROXY)
				.seesOnConsole("uses Dialectica from " + CLASHING_JAR, "identica-example-addon.jar");
	}

	static final class ClashingPlugin implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.plugin(CLASHING_JAR, "clashing-plugin");
		}
	}
}
