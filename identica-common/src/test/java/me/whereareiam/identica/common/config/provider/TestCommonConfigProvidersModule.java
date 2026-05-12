package me.whereareiam.identica.common.config.provider;

import com.google.inject.*;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.configura.merge.defaults.MergeDefaultsProvider;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.registry.ReloadableRegistry;
import me.whereareiam.identica.config.ConfigProvider;

import java.nio.file.Path;

@RequiredArgsConstructor
public final class TestCommonConfigProvidersModule extends AbstractModule {
	private final Path basePath;

	@Override
	protected void configure() {
		bind(new TypeLiteral<Registry<Reloadable>>() {}).to(ReloadableRegistry.class).asEagerSingleton();

		bind(TestAlphaCommonProvider.class).asEagerSingleton();
		bind(TestBetaCommonProvider.class).asEagerSingleton();
		bind(TestGammaCommonProvider.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	@Named("basePath")
	Path provideBasePath() {
		return basePath;
	}

	@Singleton
	public static final class TestAlphaCommonProvider extends ConfigProvider<TestDocument> {
		@Inject
		private TestAlphaCommonProvider(@Named("basePath") Path basePath, Registry<Reloadable> registry) {
			super(basePath, "alpha-common", TestDocument.class, registry, configure(TestDefaults.class, TestDocument.class));
		}
	}

	@Singleton
	public static final class TestBetaCommonProvider extends ConfigProvider<TestDocument> {
		@Inject
		private TestBetaCommonProvider(@Named("basePath") Path basePath, Registry<Reloadable> registry) {
			super(basePath, "beta-common", TestDocument.class, registry, configure(TestDefaults.class, TestDocument.class));
		}
	}

	@Singleton
	public static final class TestGammaCommonProvider extends ConfigProvider<TestDocument> {
		@Inject
		private TestGammaCommonProvider(@Named("basePath") Path basePath, Registry<Reloadable> registry) {
			super(basePath.resolve("nested-common"), "gamma-common", TestDocument.class, registry, configure(TestDefaults.class, TestDocument.class));
		}
	}

	public static class TestDocument {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	@Singleton
	public static class TestDefaults implements MergeDefaultsProvider<TestDocument> {
		@Override
		public TestDocument supply(TestDocument config) {
			config.setValue("prepared");
			return config;
		}
	}
}
