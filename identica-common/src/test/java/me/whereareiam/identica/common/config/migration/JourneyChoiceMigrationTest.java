package me.whereareiam.identica.common.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Journey Choice Migration")
class JourneyChoiceMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("Lets the player choose where a registration was interactive, and Identica pick everywhere else")
	@Test
	void replacesJourneyModesWithTheChoice() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("engine", """
				scenarios:
				  authentication:
				    pipelineTtl: "5m"
				    journeyMode: "INTERACTIVE"
				    journeyPolicy: "STRICT"
				  registration:
				    pipelineTtl: "5m"
				    journeyMode: "INTERACTIVE"
				    journeyPolicy: "PREFER"
				    autoSelectSingleProvider: true
				  migration:
				    pipelineTtl: "5m"
				    journeyMode: "SEAMLESS"
				""");

		directory.migrate(new JourneyChoiceMigration(".yml"));

		assertEquals("""
				scenarios:
				  authentication:
				    pipelineTtl: "5m"
				    choice: "AUTOMATIC"
				  registration:
				    pipelineTtl: "5m"
				    choice: "PROMPTED"
				  migration:
				    pipelineTtl: "5m"
				    choice: "AUTOMATIC"
				""", directory.files().get("engine.yml"));
	}

	@DisplayName("Lets Identica pick a seamless registration")
	@Test
	void seamlessRegistrationBecomesAutomatic() {
		DataDirectory directory = new DataDirectory(dataPath, Format.JSON);
		directory.write("engine", "{\"scenarios\":{\"registration\":{\"journeyMode\":\"SEAMLESS\",\"journeyPolicy\":\"STRICT\"}}}");

		directory.migrate(new JourneyChoiceMigration(".json"));

		assertEquals("AUTOMATIC", directory.at("engine", "/scenarios/registration/choice").asText());
		assertTrue(directory.at("engine", "/scenarios/registration/journeyPolicy").isMissingNode());
	}

	@DisplayName("Keeps a choice that is already there")
	@Test
	void keepsAnExistingChoice() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		String engine = "scenarios:\n  registration:\n    choice: \"PROMPTED\"\n";
		directory.write("engine", engine);

		directory.migrate(new JourneyChoiceMigration(".yml"));

		assertEquals(engine, directory.files().get("engine.yml"));
	}

	@DisplayName("Drops the refusal for needed input and names options in the default choice entries")
	@Test
	void updatesTheMessages() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("messages", """
				engine:
				  journey:
				    step:
				      interactionRequired:
				        - "Needs input"
				      enrollment:
				        entryFormat:
				          format: "   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray> <white>{description}</click>"
				          emptyFormat: "custom {providerId}"
				""");

		directory.migrate(new JourneyChoiceMigration(".yml"));

		assertEquals("""
				engine:
				  journey:
				    step:
				      enrollment:
				        entryFormat:
				          format: "   <dark_gray><click:run_command:/identica enroll {optionId}>▪ <gray>[{optionName}]:</gray> <white>{description}</click>"
				          emptyFormat: "custom {providerId}"
				""", directory.files().get("messages.yml"));
	}
}
