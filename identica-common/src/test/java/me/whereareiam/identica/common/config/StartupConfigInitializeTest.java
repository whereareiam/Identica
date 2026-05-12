package me.whereareiam.identica.common.config;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import me.whereareiam.identica.common.config.provider.TestCommonConfigProvidersModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Startup Config Initialize")
class StartupConfigInitializeTest {
	@DisplayName("Initialize collects and prepares all common config providers")
	@Test
	void bootstrapCollectsAndPreparesAllCommonConfigProviders(@TempDir Path tempDir) {
		var injector = Guice.createInjector(new TestConfigModule(tempDir), new TestCommonConfigProvidersModule(tempDir));

		ConfigInitializer.initialize(injector);

		assertTrue(Files.exists(tempDir.resolve("alpha-common.yml")));
		assertTrue(Files.exists(tempDir.resolve("beta-common.yml")));
		assertTrue(Files.exists(tempDir.resolve("nested-common").resolve("gamma-common.yml")));
	}

	@DisplayName("Initialize remains idempotent when config files already exist")
	@Test
	void bootstrapRemainsIdempotentWhenConfigFilesAlreadyExist(@TempDir Path tempDir) {
		var injector = Guice.createInjector(new TestConfigModule(tempDir), new TestCommonConfigProvidersModule(tempDir));

		ConfigInitializer.initialize(injector);
		ConfigInitializer.initialize(injector);

		assertTrue(Files.exists(tempDir.resolve("alpha-common.yml")));
		assertTrue(Files.exists(tempDir.resolve("beta-common.yml")));
		assertTrue(Files.exists(tempDir.resolve("nested-common").resolve("gamma-common.yml")));
	}

	private static final class TestConfigModule extends AbstractModule {
		private final Path basePath;

		private TestConfigModule(Path basePath) {
			this.basePath = basePath;
		}

		@Override
		protected void configure() {
		}
	}
}
