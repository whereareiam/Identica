package me.whereareiam.identica.common.config.migration;

import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.testing.DataDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Username Conflict Rules Migration")
class UsernameConflictRulesMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("Deletes rules of the earlier layout, the providers' conflicts and the messages only they used")
	@Test
	void replacesEarlierRules() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("identity/username/conflicts", "rules:\n  username:\n    cases: []\n");
		directory.write("providers/conflicts", "rules: {}\n");
		directory.write("identity/username/messages", """
				pipeline:
				  policy:
				    taken: ["taken"]
				    conflictDenied: ["denied"]
				    entrypointRequired: ["entrypoint"]
				""");

		directory.migrate(new UsernameConflictRulesMigration(".yml"));

		assertFalse(directory.files().containsKey("identity/username/conflicts.yml"));
		assertFalse(directory.files().containsKey("providers/conflicts.yml"));
		assertEquals("pipeline:\n  policy:\n    taken:\n      - \"taken\"\n", directory.files().get("identity/username/messages.yml"));
	}

	@DisplayName("Keeps rules of the current layout")
	@Test
	void keepsCurrentRules() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		String rules = "rules:\n  - outcome: \"REFUSE\"\n";
		directory.write("identity/username/conflicts", rules);

		directory.migrate(new UsernameConflictRulesMigration(".yml"));

		assertEquals(rules, directory.files().get("identity/username/conflicts.yml"));
	}
}
