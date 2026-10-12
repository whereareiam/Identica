package me.whereareiam.identica.feature.restriction.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Restriction Layout Migration")
class RestrictionLayoutMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The toggle lifetime and namespace of the May builds move into the feature's settings")
	@Test
	void maySettingsMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", "connection:\n  providerJoinRestrictionTtl: \"30d\"\n  sentinels:\n    resumeSpam: {}\n");
		directory.write("replication", "cache:\n  providerJoinRestrictions: \"network:join\"\n  reservations: \"network:reservation\"\n");

		directory.migrate(new RestrictionLayoutMigration(".yml"));

		assertEquals("toggleTtl: \"30d\"\nreplication:\n  toggleNamespace: \"network:join\"\n", directory.files().get("features/restriction/settings.yml"));
		assertEquals("connection:\n  sentinels:\n    resumeSpam: {}\n", directory.files().get("settings.yml"));
		assertEquals("cache:\n  reservations: \"network:reservation\"\n", directory.files().get("replication.yml"));
	}

	@DisplayName("The June builds' toggle lifetime and capability document move, the document's value winning")
	@Test
	void juneSettingsMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/providers", "behavior:\n  attemptTtl: \"10m\"\n  joinRestrictionToggleTtl: \"5d\"\n");
		directory.write("providers/capabilities/restriction/settings", "_version: 1\ntoggleTtl: \"10d\"\n");

		directory.migrate(new RestrictionLayoutMigration(".yml"));

		assertEquals("toggleTtl: \"10d\"\n", directory.files().get("features/restriction/settings.yml"));
		assertEquals("behavior:\n  attemptTtl: \"10m\"\n", directory.files().get("providers/providers.yml"));
		assertFalse(directory.exists("providers/capabilities/restriction/settings"));
	}
}
