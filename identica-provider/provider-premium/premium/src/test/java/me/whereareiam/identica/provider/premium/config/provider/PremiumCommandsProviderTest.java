package me.whereareiam.identica.provider.premium.config.provider;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("Premium Commands Provider")
class PremiumCommandsProviderTest {
	@TempDir
	Path directory;
	private Configura previous;

	@BeforeEach
	void useYaml() {
		previous = Config.configured();
		Config.setConfigured(Config.yaml());
	}

	@AfterEach
	void restoreConfigura() {
		Config.setConfigured(previous);
	}

	@DisplayName("The command document holds command definitions only, without core behavior")
	@Test
	@SuppressWarnings("unchecked")
	void commandDocumentHoldsDefinitionsOnly() throws IOException {
		assertFalse(new PremiumCommandsProvider(directory, mock(Registry.class)).get().getCommands().isEmpty());

		String written = Files.readString(directory.resolve("commands.yml"));
		assertTrue(written.startsWith("commands:\n"), written);
		assertFalse(written.contains("behavior"), written);
	}
}
