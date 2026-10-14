package me.whereareiam.identica.common.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Session Policy Migration")
class SessionPolicyMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("Replaces ALLOW_MULTIPLE with REPLACE_EXISTING in the settings and in each provider entry")
	@Test
	void replacesAllowMultiple() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", "level: 3\nsessions:\n  concurrencyPolicy: \"ALLOW_MULTIPLE\"\n");
		directory.write("providers/providers", """
				providers:
				  - id: "credential"
				    session:
				      concurrencyPolicy: "ALLOW_MULTIPLE"
				  - id: "premium"
				    session:
				      concurrencyPolicy: "REJECT_NEW"
				  - id: "example"
				""");

		directory.migrate(new SessionPolicyMigration(".yml"));

		assertEquals("level: 3\nsessions:\n  concurrencyPolicy: \"REPLACE_EXISTING\"\n", directory.files().get("settings.yml"));
		assertEquals("""
				providers:
				  - id: "credential"
				    session:
				      concurrencyPolicy: "REPLACE_EXISTING"
				  - id: "premium"
				    session:
				      concurrencyPolicy: "REJECT_NEW"
				  - id: "example"
				""", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("Keeps the policies that still exist")
	@Test
	void keepsExistingPolicies() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		String settings = "sessions:\n  concurrencyPolicy: \"REJECT_NEW\"\n";
		directory.write("settings", settings);

		directory.migrate(new SessionPolicyMigration(".yml"));

		assertEquals(settings, directory.files().get("settings.yml"));
	}

	@DisplayName("Replaces ALLOW_MULTIPLE in JSON documents too")
	@Test
	void replacesAllowMultipleInJson() {
		DataDirectory directory = new DataDirectory(dataPath, Format.JSON);
		directory.write("providers/providers", "{\"providers\":[{\"id\":\"credential\",\"session\":{\"concurrencyPolicy\":\"ALLOW_MULTIPLE\"}}]}");

		directory.migrate(new SessionPolicyMigration(".json"));

		assertEquals("REPLACE_EXISTING", directory.at("providers/providers", "/providers/0/session/concurrencyPolicy").asText());
	}
}
