package me.whereareiam.identica.common.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;
import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Connection Layout Migration")
class ConnectionLayoutMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The connection settings of 2.2.1 move into the settings, engine, routing and provider documents")
	@Test
	void release221SettingsSplit() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", """
				_version: 1
				level: 3
				connection:
				  handshakeInstructionTtl: "7m"
				  attemptTtl: "8m"
				  reservationTtl: "20m"
				  prepareStateTtl: "9m"
				  uniqueIdMode: "OFFLINE"
				  routing:
				    defaults:
				      complete:
				        target: "lobby"
				    scenarios: {}
				  sessions:
				    concurrencyPolicy: "REJECT_NEW"
				    activeTtl: "6h"
				  scenarios:
				    authentication:
				      pipelineTtl: "4m"
				      advanceLockTtl: "3s"
				      journeyMode: "INTERACTIVE"
				listeners:
				  events: {}
				""");
		directory.write("providers/providers", "providers: []\n");

		directory.migrate(new ConnectionLayoutMigration(".yml"));

		assertEquals("""
				_version: 1
				level: 3
				listeners:
				  events: {}
				identity:
				  reservationTtl: "20m"
				  uniqueIdMode: "OFFLINE"
				sessions:
				  concurrencyPolicy: "REJECT_NEW"
				""", directory.files().get("settings.yml"));
		assertEquals("""
				behavior:
				  handshakeInstructionTtl: "7m"
				  bridgeTtl: "9m"
				scenarios:
				  authentication:
				    pipelineTtl: "4m"
				    journeyMode: "INTERACTIVE"
				""", directory.files().get("engine.yml"));
		assertEquals("defaults:\n  complete:\n    target: \"lobby\"\nscenarios: {}\n", directory.files().get("routing.yml"));
		assertEquals("providers: []\nbehavior:\n  attemptTtl: \"8m\"\n", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("The connection messages of 2.2.1 move under scenarios and engine")
	@Test
	void release221MessagesSplit() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("messages", """
				prefix: "Net"
				connection:
				  concurrentLoginKick: ["Kicked"]
				  authentication:
				    pipelineKick: ["Busy"]
				    advanceBusy:
				      chat: "Wait"
				    routing:
				      missingServer: ["No login server"]
				  registration:
				    routing:
				      missingServer: ["No registration server"]
				  journey:
				    step:
				      noStatus: ["Nothing"]
				  prepare:
				    handshakeDenied: ["Denied"]
				commands:
				  admin:
				    sessions:
				      info:
				        notFound: "None"
				""");

		directory.migrate(new ConnectionLayoutMigration(".yml"));

		assertEquals("""
				prefix: "Net"
				commands:
				  admin:
				    sessions:
				      status:
				        notFound: "None"
				scenarios:
				  authentication:
				    pipelineKick:
				      - "Busy"
				    routing:
				      missingServer:
				        - "No login server"
				  registration:
				    routing:
				      missingServer:
				        - "No registration server"
				engine:
				  journey:
				    step:
				      noStatus:
				        - "Nothing"
				  prepare:
				    handshakeDenied:
				      - "Denied"
				  concurrentLoginKick:
				    - "Kicked"
				""", directory.files().get("messages.yml"));
	}

	@DisplayName("The advance lock of the June builds is dropped and the rest of the engine document stays")
	@Test
	void juneAdvanceLockIsDropped() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("engine", "scenarios:\n  migration:\n    advanceLockTtl: \"5s\"\n    allowResume: true\n");
		directory.write("messages", "scenarios:\n  migration:\n    advanceBusy:\n      chat: \"Wait\"\n    cancelled: [\"Cancelled\"]\n");

		directory.migrate(new ConnectionLayoutMigration(".yml"));

		assertEquals("scenarios:\n  migration:\n    allowResume: true\n", directory.files().get("engine.yml"));
		assertEquals("scenarios:\n  migration:\n    cancelled:\n      - \"Cancelled\"\n", directory.files().get("messages.yml"));
	}

	@DisplayName("What a feature collects stays in the connection sections, which go once they are empty")
	@Test
	void featureSectionsStay() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("settings", """
				connection:
				  attemptTtl: "8m"
				  sessions:
				    concurrencyPolicy: "REJECT_NEW"
				    recognition:
				      enabled: true
				  sentinels:
				    resumeSpam:
				      enabled: true
				  scenarios:
				    registration:
				      allowResume: true
				      allowProviderRestrictionResumeBypass: true
				""");
		directory.write("messages", """
				connection:
				  resumeSentineled: ["Slow down"]
				  prepare:
				    handshakeDenied: ["Denied"]
				    providerRestricted: ["Unused"]
				  authentication:
				    pipelineKick: ["Busy"]
				""");

		directory.migrate(new ConnectionLayoutMigration(".yml"));

		assertEquals("""
				connection:
				  sessions:
				    recognition:
				      enabled: true
				  sentinels:
				    resumeSpam:
				      enabled: true
				  scenarios:
				    registration:
				      allowProviderRestrictionResumeBypass: true
				sessions:
				  concurrencyPolicy: "REJECT_NEW"
				""", directory.files().get("settings.yml"));
		assertEquals("""
				connection:
				  resumeSentineled:
				    - "Slow down"
				  prepare:
				    providerRestricted:
				      - "Unused"
				scenarios:
				  authentication:
				    pipelineKick:
				      - "Busy"
				engine:
				  prepare:
				    handshakeDenied:
				      - "Denied"
				""", directory.files().get("messages.yml"));
	}
}
