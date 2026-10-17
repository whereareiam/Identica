package me.whereareiam.identica.common.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Command Cooldowns Migration")
class CommandCooldownsMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("Gives the commands with the earlier shared default a cooldown of their own and keeps the others")
	@Test
	void replacesTheEarlierSharedDefault() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("commands", """
				commands:
				  help:
				    cooldown:
				      enabled: true
				      duration: 2
				      group: "global"
				  reload:
				    cooldown:
				      enabled: true
				      duration: 5
				      group: "global"
				  enroll:
				    cooldown:
				      enabled: false
				      duration: 2
				      group: "global"
				  admin:
				    cooldown:
				      enabled: true
				      duration: 2
				      group: "staff"
				""");

		directory.migrate(new CommandCooldownsMigration(".yml"));

		assertEquals("""
				commands:
				  help:
				    cooldown:
				      enabled: true
				      duration: 2
				      group: ""
				  reload:
				    cooldown:
				      enabled: true
				      duration: 5
				      group: "global"
				  enroll:
				    cooldown:
				      enabled: false
				      duration: 2
				      group: "global"
				  admin:
				    cooldown:
				      enabled: true
				      duration: 2
				      group: "staff"
				""", directory.files().get("commands.yml"));
	}

	@DisplayName("Replaces the earlier shared default in JSON documents too")
	@Test
	void replacesTheEarlierSharedDefaultInJson() {
		DataDirectory directory = new DataDirectory(dataPath, Format.JSON);
		directory.write("commands", "{\"commands\":{\"help\":{\"cooldown\":{\"enabled\":true,\"duration\":2,\"group\":\"global\"}}}}");

		directory.migrate(new CommandCooldownsMigration(".json"));

		assertEquals("", directory.at("commands", "/commands/help/cooldown/group").asText());
	}

	@DisplayName("Leaves a directory without a commands document alone")
	@Test
	void leavesAMissingDocumentAlone() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);

		directory.migrate(new CommandCooldownsMigration(".yml"));

		assertFalse(directory.exists("commands"));
	}
}
