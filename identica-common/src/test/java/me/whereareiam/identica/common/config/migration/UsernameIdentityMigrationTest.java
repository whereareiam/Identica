package me.whereareiam.identica.common.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;
import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Username Identity Migration")
class UsernameIdentityMigrationTest {
	private static final String RULES_221 = """
			conflicts:
			  username:
			    pairs:
			      - providers: ["premium", "credential"]
			        force: true
			        resolvers:
			          - id: "format_display"
			            parameters:
			              target: "joiner"
			    default:
			      providers: []
			      force: false
			      resolvers: []
			  email:
			    pairs: []
			    default:
			      providers: []
			      force: true
			      resolvers: []
			providers:
			  - id: "credential"
			""";

	@TempDir
	Path dataPath;

	@DisplayName("The username rules of 2.2.1 become cases in the username document, other keys stay with the providers")
	@Test
	void release221RulesBecomeCases() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/providers", RULES_221);

		directory.migrate(new UsernameIdentityMigration(".yml"));

		assertEquals("""
				rules:
				  username:
				    default:
				      when: {}
				      force: false
				      resolvers: []
				    cases:
				      - when:
				          providers:
				            - "premium"
				            - "credential"
				        force: true
				        resolvers:
				          - id: "format_display"
				            parameters:
				              target: "joiner"
				""", directory.files().get("identity/username/conflicts.yml"));
		assertEquals("rules:\n  email:\n    default:\n      when: {}\n      force: true\n      resolvers: []\n    cases: []\n",
				directory.files().get("providers/conflicts.yml"));
		assertEquals("providers:\n  - id: \"credential\"\n", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("Username rules in the providers' conflicts move when the directory has no username document yet")
	@Test
	void providerRulesMoveBeforeTheSplit() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/conflicts", "_version: 1\nrules:\n  username:\n    pairs:\n      - providers: [\"a\", \"b\"]\n        force: true\n");

		directory.migrate(new UsernameIdentityMigration(".yml"));

		assertEquals("rules:\n  username:\n    cases:\n      - when:\n          providers:\n            - \"a\"\n            - \"b\"\n        force: true\n",
				directory.files().get("identity/username/conflicts.yml"));
		assertEquals("_version: 1\n", directory.files().get("providers/conflicts.yml"));
	}

	@DisplayName("Username rules in the providers' conflicts stay as an override once the username document exists")
	@Test
	void providerRulesStayAfterTheSplit() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/conflicts", "rules:\n  username:\n    cases: []\n");
		directory.write("identity/username/conflicts", "rules:\n  username:\n    cases: []\n");

		directory.migrate(new UsernameIdentityMigration(".yml"));

		assertEquals("rules:\n  username:\n    cases: []\n", directory.files().get("providers/conflicts.yml"));
	}

	@DisplayName("The authentication scenario's username messages become the shared ones, the others are dropped")
	@Test
	void scenarioMessagesBecomeShared() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("messages", """
				connection:
				  authentication:
				    conflictEntrypointRequired: ["Use your host"]
				    errors:
				      identity:
				        replicationMissing: ["Login sync"]
				        accountMissing: ["No account"]
				  registration:
				    errors:
				      identity:
				        replicationMissing: ["Register sync"]
				""");

		directory.migrate(new UsernameIdentityMigration(".yml"));

		assertEquals("""
				pipeline:
				  identity:
				    synchronizationFailed:
				      - "Login sync"
				  policy:
				    entrypointRequired:
				      - "Use your host"
				""", directory.files().get("identity/username/messages.yml"));
		assertEquals("""
				connection:
				  authentication:
				    errors:
				      identity:
				        accountMissing:
				          - "No account"
				""", directory.files().get("messages.yml"));
	}

	@DisplayName("A June build's capability document wins over the messages it also kept")
	@Test
	void capabilityMessagesWin() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("messages", "scenarios:\n  authentication:\n    conflictEntrypointRequired: [\"old\"]\n");
		directory.write("providers/capabilities/authoritative-username/messages", "_version: 1\npipeline:\n  policy:\n    entrypointRequired: [\"new\"]\n");

		directory.migrate(new UsernameIdentityMigration(".yml"));

		assertEquals("pipeline:\n  policy:\n    entrypointRequired:\n      - \"new\"\n", directory.files().get("identity/username/messages.yml"));
		assertFalse(directory.exists("providers/capabilities/authoritative-username/messages"));
	}
}
