package me.whereareiam.identica.feature.verification.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;

import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Verification Layout Migration")
class VerificationLayoutMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The verification document, messages and commands of 2.2.1 move into the feature's documents")
	@Test
	void release221VerificationMoves() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("verification", "_version: 1\nchallengeTtl: \"3m\"\ntotp:\n  digits: 8\n");
		directory.write("messages", """
				commands:
				  help:
				    header: "Help"
				  verification:
				    playerOnly: "Players only"
				    methods:
				      totp:
				        pending: "Pending"
				  admin:
				    verification:
				      reset:
				        completed: "Reset"
				    sessions:
				      unknown: "Unknown"
				""");
		directory.write("commands", """
				commands:
				  main:
				    aliases: ["identica"]
				  verification:
				    aliases: ["2fa", "twofactor"]
				  admin-verification-reset:
				    aliases: ["admin 2fa reset"]
				""");

		directory.migrate(new VerificationLayoutMigration(".yml"));

		assertFalse(directory.exists("verification"));
		assertEquals("challengeTtl: \"3m\"\ntotp:\n  digits: 8\n", directory.files().get("features/verification/settings.yml"));
		assertEquals("""
				methods:
				  totp:
				    pending: "Pending"
				commands:
				  playerOnly: "Players only"
				  admin:
				    reset:
				      completed: "Reset"
				""", directory.files().get("features/verification/messages.yml"));
		assertEquals("commands:\n  help:\n    header: \"Help\"\n  admin:\n    sessions:\n      unknown: \"Unknown\"\n",
				directory.files().get("messages.yml"));
		assertEquals("""
				commands:
				  verification:
				    aliases:
				      - "2fa"
				      - "twofactor"
				  admin-verification-reset:
				    aliases:
				      - "admin 2fa reset"
				""", directory.files().get("features/verification/commands.yml"));
		assertEquals("commands:\n  main:\n    aliases:\n      - \"identica\"\n", directory.files().get("commands.yml"));
	}

	@DisplayName("A definition the feature's commands document has already is kept from there")
	@Test
	void featureCommandsWin() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("commands", "commands:\n  verification:\n    aliases: [\"old\"]\n");
		directory.write("features/verification/commands", "commands:\n  verification:\n    aliases: [\"new\"]\n");

		directory.migrate(new VerificationLayoutMigration(".yml"));

		assertEquals("commands:\n  verification:\n    aliases: [\"new\"]\n", directory.files().get("features/verification/commands.yml"));
		assertEquals("{}\n", directory.files().get("commands.yml"));
	}
}
