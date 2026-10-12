package me.whereareiam.identica.common.config.migration;

import me.whereareiam.identica.common.config.testing.DataDirectory;
import me.whereareiam.configura.type.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Document Versions Migration")
class DocumentVersionsMigrationTest {
	@TempDir
	Path dataPath;

	@DisplayName("The versions Configura 1.x wrote leave every core and feature document")
	@Test
	void versionsLeaveCoreAndFeatureDocuments() {
		DataDirectory directory = new DataDirectory(dataPath, Format.YAML);
		for (String document : DocumentVersionsMigration.DOCUMENTS)
			directory.write(document, "_version: 1\nkept: true\n");

		directory.migrate(new DocumentVersionsMigration(".yml"));

		for (String document : DocumentVersionsMigration.DOCUMENTS)
			assertEquals("kept: true\n", directory.files().get(document + ".yml"), document);
	}

	@DisplayName("The settings and the documents of a provider keep theirs")
	@Test
	void settingsAndProviderDocumentsKeepTheirs() {
		DataDirectory directory = new DataDirectory(dataPath, Format.JSON);
		directory.write("settings", "{\"_version\": 1, \"level\": 2}");
		directory.write("providers/Credential/settings", "{\"_version\": 1, \"minimumLength\": 6}");

		directory.migrate(new DocumentVersionsMigration(".json"));

		assertEquals("{\"_version\": 1, \"level\": 2}", directory.files().get("settings.json"));
		assertEquals("{\"_version\": 1, \"minimumLength\": 6}", directory.files().get("providers/Credential/settings.json"));
	}
}
