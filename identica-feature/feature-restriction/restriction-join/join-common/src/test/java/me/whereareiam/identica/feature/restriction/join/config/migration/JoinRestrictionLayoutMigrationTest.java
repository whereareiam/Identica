package me.whereareiam.identica.feature.restriction.join.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Join Restriction Layout Migration")
class JoinRestrictionLayoutMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The join restriction of the May builds moves out of the core's documents")
	@Test
	void mayJoinRestrictionMoves() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", """
				connection:
				  scenarios:
				    authentication:
				      allowProviderRestrictionResumeBypass: true
				    registration:
				      allowProviderRestrictionResumeBypass: false
				    migration:
				      allowProviderRestrictionResumeBypass: true
				""");
		directory.write("messages", """
				connection:
				  prepare:
				    providerRestricted: ["Unused"]
				  providerRestriction:
				    denied: ["Not now"]
				commands:
				  admin:
				    providerRestriction:
				      enabled: "Enabled"
				      status:
				        body: ["Status"]
				    sessions:
				      unknown: "Unknown"
				""");
		directory.write("commands", """
				commands:
				  admin-provider-restriction-enable:
				    aliases: ["admin provider restriction enable"]
				  admin-provider-restriction-status:
				    usage: "{command} {alias} [provider]"
				  main:
				    aliases: ["identica"]
				""");

		directory.migrate(new JoinRestrictionLayoutMigration(".yml"));

		assertEquals("resumeBypass:\n  authentication: true\n  registration: false\n", directory.files().get("features/restriction/join/settings.yml"));
		assertEquals("{}\n", directory.files().get("settings.yml"));
		assertEquals("commands:\n  enabled: \"Enabled\"\n  status:\n    body:\n      - \"Status\"\ndenied:\n  - \"Not now\"\n",
				directory.files().get("features/restriction/join/messages.yml"));
		assertEquals("commands:\n  admin:\n    sessions:\n      unknown: \"Unknown\"\n", directory.files().get("messages.yml"));
		assertEquals("""
				commands:
				  admin-provider-restriction-join-enable:
				    aliases:
				      - "admin provider restriction enable"
				  admin-provider-restriction-join-status:
				    usage: "{command} {alias} [provider]"
				""", directory.files().get("features/restriction/join/commands.yml"));
		assertEquals("commands:\n  main:\n    aliases:\n      - \"identica\"\n", directory.files().get("commands.yml"));
	}

	@DisplayName("The documents of the later June builds move under features, and the engine's bypass leaves")
	@Test
	void juneDocumentsMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/capabilities/restriction/join/messages", "_version: 1\ndenied: [\"Closed\"]\n");
		directory.write("providers/capabilities/restriction/join/commands", "_version: 1\ncommands:\n  admin-provider-restriction-join-enable:\n    enabled: false\n");
		directory.write("engine", "scenarios:\n  registration:\n    allowProviderRestrictionResumeBypass: true\n    pipelineTtl: \"5m\"\n");

		directory.migrate(new JoinRestrictionLayoutMigration(".yml"));

		assertEquals("denied:\n  - \"Closed\"\n", directory.files().get("features/restriction/join/messages.yml"));
		assertEquals("commands:\n  admin-provider-restriction-join-enable:\n    enabled: false\n", directory.files().get("features/restriction/join/commands.yml"));
		assertEquals("resumeBypass:\n  registration: true\n", directory.files().get("features/restriction/join/settings.yml"));
		assertEquals("scenarios:\n  registration:\n    pipelineTtl: \"5m\"\n", directory.files().get("engine.yml"));
		assertFalse(directory.exists("providers/capabilities/restriction/join/messages"));
		assertFalse(directory.exists("providers/capabilities/restriction/join/commands"));
	}
}
