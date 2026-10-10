package me.whereareiam.identica.common.registry;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigExtensionProvider;
import me.whereareiam.identica.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Reloadable Registry")
class ReloadableRegistryTest {
	@DisplayName("Reloads a document before the extensions of it, whatever order they registered in")
	@Test
	void ownersComeBeforeExtensions() {
		ReloadableRegistry registry = new ReloadableRegistry();
		Reloadable extension = new AaaExtension(registry);
		Reloadable second = new ZzzDocument(registry);
		Reloadable first = new MmmDocument(registry);

		assertEquals(List.of(first, second, extension), List.copyOf(registry.values()));
	}

	private static final class AaaExtension extends ConfigExtensionProvider<Object> {
		private AaaExtension(Registry<Reloadable> registry) {
			super(Path.of("providers"), "providers", Object.class, registry);
		}

		@Override
		protected Configura configura() {
			return Config.configured();
		}
	}

	private static final class MmmDocument extends ConfigProvider<Object> {
		private MmmDocument(Registry<Reloadable> registry) {
			super(Path.of("."), "settings", Object.class, registry);
		}

		@Override
		protected Configura configura() {
			return Config.configured();
		}
	}

	private static final class ZzzDocument extends ConfigProvider<Object> {
		private ZzzDocument(Registry<Reloadable> registry) {
			super(Path.of("providers"), "providers", Object.class, registry);
		}

		@Override
		protected Configura configura() {
			return Config.configured();
		}
	}
}
