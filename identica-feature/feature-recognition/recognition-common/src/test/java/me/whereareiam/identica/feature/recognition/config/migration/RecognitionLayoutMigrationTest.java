package me.whereareiam.identica.feature.recognition.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;

import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Recognition Layout Migration")
class RecognitionLayoutMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The recognition settings of 2.2.1 move into the feature's document")
	@Test
	void release221SettingsMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", """
				level: 2
				connection:
				  sessions:
				    concurrencyPolicy: "REJECT_NEW"
				    recognition:
				      enabled: true
				      validity: "6h"
				      defaultSignals: ["IP"]
				      untrustedIps:
				        enabled: false
				        entries: ["10.0.0.0/8"]
				""");
		directory.write("replication", """
				cache:
				  sessions:
				    session: "network:sessions:session"
				    recognition:
				      snapshot: "network:snapshot"
				""");

		directory.migrate(new RecognitionLayoutMigration(".yml"));

		assertEquals("""
				enabled: true
				validity: "6h"
				defaultSignals:
				  - "IP"
				eligibility:
				  untrustedIps:
				    enabled: false
				    entries:
				      - "10.0.0.0/8"
				replication:
				  snapshotNamespace: "network:snapshot"
				""", directory.files().get("features/recognition/settings.yml"));
		assertEquals("level: 2\nconnection:\n  sessions:\n    concurrencyPolicy: \"REJECT_NEW\"\n", directory.files().get("settings.yml"));
		assertEquals("cache:\n  sessions:\n    session: \"network:sessions:session\"\n", directory.files().get("replication.yml"));
	}

	@DisplayName("Where two places hold a setting, the newer one's is kept")
	@Test
	void juneSettingsMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", """
				sessions:
				  recognition:
				    validity: "3h"
				    eligibility:
				      untrustedIps:
				        enabled: false
				""");
		directory.write("providers/capabilities/recognition/settings", "_version: 1\nwindow: \"5m\"\nvalidity: \"9h\"\n");

		directory.migrate(new RecognitionLayoutMigration(".yml"));

		assertEquals("window: \"5m\"\nvalidity: \"9h\"\neligibility:\n  untrustedIps:\n    enabled: false\n",
				directory.files().get("features/recognition/settings.yml"));
		assertFalse(directory.exists("providers/capabilities/recognition/settings"));
	}

	@DisplayName("A directory in the current layout is left as it is")
	@Test
	void currentLayoutIsUntouched() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", "level: 2\nsessions:\n  concurrencyPolicy: \"REJECT_NEW\"\n");
		directory.write("features/recognition/settings", "enabled: true\n");

		directory.migrate(new RecognitionLayoutMigration(".yml"));

		assertEquals("level: 2\nsessions:\n  concurrencyPolicy: \"REJECT_NEW\"\n", directory.files().get("settings.yml"));
		assertEquals("enabled: true\n", directory.files().get("features/recognition/settings.yml"));
	}
}
