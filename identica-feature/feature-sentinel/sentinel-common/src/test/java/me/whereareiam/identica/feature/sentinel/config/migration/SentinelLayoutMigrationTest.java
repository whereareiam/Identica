package me.whereareiam.identica.feature.sentinel.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;

import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sentinel Layout Migration")
class SentinelLayoutMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The sentinels, their namespace and their message of 2.2.1 move into the feature's documents")
	@Test
	void release221SentinelsMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", """
				connection:
				  attemptTtl: "8m"
				  sentinels:
				    resumeSpam:
				      enabled: true
				      maxAttempts: 5
				""");
		directory.write("replication", "cache:\n  reservations: \"network:reservation\"\n  sentinels: \"network:sentinels\"\n");
		directory.write("messages", "connection:\n  resumeSentineled:\n    - \"Slow down\"\n  concurrentLoginKick:\n    - \"Bye\"\n");

		directory.migrate(new SentinelLayoutMigration(".yml"));

		assertEquals("sentinels:\n  resumeSpam:\n    enabled: true\n    maxAttempts: 5\nreplication:\n  state: \"network:sentinels\"\n",
				directory.files().get("features/sentinel/settings.yml"));
		assertEquals("resumeSpam:\n  denied:\n    - \"Slow down\"\n", directory.files().get("features/sentinel/messages.yml"));
		assertEquals("connection:\n  attemptTtl: \"8m\"\n", directory.files().get("settings.yml"));
		assertEquals("cache:\n  reservations: \"network:reservation\"\n", directory.files().get("replication.yml"));
		assertEquals("connection:\n  concurrentLoginKick:\n    - \"Bye\"\n", directory.files().get("messages.yml"));
	}

	@DisplayName("A June build's feature document wins over the core settings it also kept")
	@Test
	void featureDocumentWins() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", "sentinels:\n  resumeSpam:\n    maxAttempts: 4\n    active: true\n");
		directory.write("features/sentinel/settings", "sentinels:\n  resumeSpam:\n    maxAttempts: 9\ncache:\n  sentinels: \"june:sentinels\"\n");
		directory.write("messages", "engine:\n  resumeSentineled:\n    - \"core\"\n");
		directory.write("features/sentinel/messages", "resumeSpam:\n  denied:\n    - \"feature\"\n");

		directory.migrate(new SentinelLayoutMigration(".yml"));

		assertEquals("sentinels:\n  resumeSpam:\n    maxAttempts: 9\n    active: true\nreplication:\n  state: \"june:sentinels\"\n",
				directory.files().get("features/sentinel/settings.yml"));
		assertEquals("resumeSpam:\n  denied:\n    - \"feature\"\n", directory.files().get("features/sentinel/messages.yml"));
		assertEquals("{}\n", directory.files().get("settings.yml"));
	}
}
