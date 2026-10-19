package me.whereareiam.identica.feature.restriction.join.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Join Restriction Command Cooldowns Migration")
class JoinRestrictionCommandCooldownsMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("Gives the commands with the earlier shared default a cooldown of their own and keeps the others")
	@Test
	void replacesTheEarlierSharedDefault() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("features/restriction/join/commands", """
				commands:
				  admin-join-restriction:
				    cooldown:
				      enabled: true
				      duration: 2
				      group: "global"
				  admin-join-restriction-list:
				    cooldown:
				      enabled: true
				      duration: 2
				      group: "staff"
				""");

		directory.migrate(new JoinRestrictionCommandCooldownsMigration(".yml"));

		assertEquals("", directory.at("features/restriction/join/commands", "/commands/admin-join-restriction/cooldown/group").asText());
		assertEquals("staff", directory.at("features/restriction/join/commands", "/commands/admin-join-restriction-list/cooldown/group").asText());
	}
}
