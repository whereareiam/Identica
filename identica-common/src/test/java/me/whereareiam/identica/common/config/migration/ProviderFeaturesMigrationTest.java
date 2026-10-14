package me.whereareiam.identica.common.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;
import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Provider Features Migration")
class ProviderFeaturesMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("2.2.1's defaults leave an entry it wrote them into, and what an operator changed moves")
	@Test
	void release221DefaultsLeaveAndChangesMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/providers", """
				providers:
				  - id: "credential"
				    enabled: true
				    overrides:
				      allowRecognitionOnUntrustedIp: false
				      recognition:
				        signals: []
				    verification:
				      enabled: true
				      required: true
				      unavailableSelectionPolicy: "KEEP_LOCKED"
				      methods:
				        - id: "totp"
				          enabled: true
				          priority: 100
				          unavailableSelectionPolicy: "KEEP_LOCKED"
				    entrypoints: []
				""");

		directory.migrate(new ProviderFeaturesMigration(".yml"));

		assertEquals("""
				providers:
				  - id: "credential"
				    enabled: true
				    entrypoints: []
				    features:
				      verification:
				        required: true
				""", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("An entry of a provider 2.2.1 did not ship moves as it is")
	@Test
	void otherProvidersMoveAsTheyAre() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/providers", """
				providers:
				  - id: "example"
				    overrides:
				      allowRecognitionOnUntrustedIp: false
				      sessionConcurrencyPolicy: "REPLACE_EXISTING"
				      recognition:
				        enabled: true
				        signals: []
				    verification:
				      enabled: true
				""");

		directory.migrate(new ProviderFeaturesMigration(".yml"));

		assertEquals("""
				providers:
				  - id: "example"
				    features:
				      recognition:
				        allowOnUntrustedIps: false
				        enabled: true
				        signals: []
				      verification:
				        enabled: true
				    session:
				      concurrencyPolicy: "REPLACE_EXISTING"
				""", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("The May builds' eligibility and join restriction move under features")
	@Test
	void mayEntriesMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/providers", """
				providers:
				  - id: "premium"
				    overrides:
				      recognition:
				        signals: ["IP"]
				        eligibility:
				          allowOnUntrustedIp: true
				    joinRestriction:
				      enabled: false
				      allow: ["RECOGNIZED"]
				""");

		directory.migrate(new ProviderFeaturesMigration(".yml"));

		assertEquals("""
				providers:
				  - id: "premium"
				    features:
				      recognition:
				        allowOnUntrustedIps: true
				        signals:
				          - "IP"
				      restriction:
				        join:
				          enabled: false
				          allow:
				            - "RECOGNIZED"
				""", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("The June builds' session, restriction and capabilities move under features")
	@Test
	void juneEntriesMove() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		directory.write("providers/providers", """
				providers:
				  - id: "example"
				    session:
				      concurrencyPolicy: "REJECT_NEW"
				      recognition:
				        enabled: false
				    restriction:
				      join:
				        enabled: true
				  - id: "other"
				    capabilities:
				      recognition:
				        allowOnUntrustedIps: true
				      restriction:
				        join:
				          allow: ["LINKED"]
				    features:
				      verification:
				        required: true
				""");

		directory.migrate(new ProviderFeaturesMigration(".yml"));

		assertEquals("""
				providers:
				  - id: "example"
				    session:
				      concurrencyPolicy: "REJECT_NEW"
				    features:
				      restriction:
				        join:
				          enabled: true
				      recognition:
				        enabled: false
				  - id: "other"
				    features:
				      verification:
				        required: true
				      recognition:
				        allowOnUntrustedIps: true
				      restriction:
				        join:
				          allow:
				            - "LINKED"
				""", directory.files().get("providers/providers.yml"));
	}

	@DisplayName("An entry in the current layout is left as it is")
	@Test
	void currentEntriesAreUntouched() {
		DataDirectory directory = new DataDirectory(dataPath, Format.JSON);
		String providers = "{\"providers\": [{\"id\": \"credential\", \"features\": {\"verification\": {\"enabled\": true}}}]}";
		directory.write("providers/providers", providers);

		directory.migrate(new ProviderFeaturesMigration(".json"));

		assertEquals(providers, directory.files().get("providers/providers.json"));
	}
}
