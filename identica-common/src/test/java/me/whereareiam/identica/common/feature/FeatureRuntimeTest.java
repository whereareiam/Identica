package me.whereareiam.identica.common.feature;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import me.whereareiam.identica.feature.*;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FeatureRuntimeTest {
	@TempDir Path path;

	@Test
	void usesOnlyCompiledFeaturesAndIgnoresFormerInstallationFiles() throws Exception {
		Files.createDirectories(path.resolve("features"));
		Files.writeString(path.resolve("features/features.properties"), "enabled=unknown");
		Files.writeString(path.resolve("features/external.jar"), "not a jar");
		try (var runtime = FeatureRuntime.prepare(path, List.of(new TestFeature("recognition")))) {
			assertEquals(Set.of("recognition"), runtime.ids());
			assertFalse(runtime.isAvailable("unknown"));
		}
	}

	@Test
	void providerOverridesAreIndependentAndMissingValuesInheritLiveGlobalDefaults() {
		var feature = new TestFeature("recognition");
		feature.enabled.set(false);
		Providers providers = new Providers();
		providers.setProviders(List.of(provider("first", true), provider("second", false), provider("inherited", null)));
		try (var runtime = FeatureRuntime.prepare(path, List.of(feature))) {
			runtime.initialize(root(runtime, providers));
			for (String id : List.of("first", "second", "inherited")) support(runtime, id, "recognition");
			assertTrue(runtime.isEnabled("first", "recognition"));
			assertFalse(runtime.isEnabled("second", "recognition"));
			assertFalse(runtime.isEnabled("inherited", "recognition"));
			assertFalse(runtime.isEnabled("unsupported", "recognition"));
			feature.enabled.set(true);
			assertTrue(runtime.isEnabled("inherited", "recognition"));
			assertFalse(runtime.isEnabled("second", "recognition"));
			providers.setProviders(List.of(provider("first", false)));
			assertFalse(runtime.isEnabled("first", "recognition"));
		}
	}

	@Test
	void configurationCannotGrantFeatureSupport() {
		Providers providers = new Providers();
		providers.setProviders(List.of(provider("unsupported", true)));
		try (var runtime = FeatureRuntime.prepare(path, List.of(new TestFeature("recognition")))) {
			runtime.initialize(root(runtime, providers));
			var error = assertThrows(IllegalStateException.class, () -> support(runtime, "unsupported"));
			assertTrue(error.getMessage().contains("does not support feature recognition"));
		}
	}

	@Test
	void nestedSwitchCanDisableJoinWithoutDisablingOtherRestrictionTypes() {
		var features = new Providers.ProviderEntry.Features();
		var restriction = JsonNodeFactory.instance.objectNode().put("enabled", true);
		restriction.putObject("join").put("enabled", false);
		features.put("restriction", restriction);
		assertEquals(true, features.enabledOverride("restriction"));
		assertEquals(false, features.enabledOverride("restriction.join"));
		restriction.put("enabled", false);
		restriction.withObject("join").put("enabled", true);
		assertEquals(false, features.enabledOverride("restriction.join"));
	}

	@Test
	void ordersDependenciesAndReversesLifecycles() {
		List<String> events = new ArrayList<>();
		var base = new TestFeature("restriction", Set.of(), events);
		var join = new TestFeature("join", Set.of("restriction"), events);
		try (var runtime = FeatureRuntime.prepare(path, List.of(join, base))) {
			runtime.initialize(root(runtime, new Providers()));
			assertEquals(List.of("restriction:start", "join:start"), events);
		}
		assertEquals(List.of("restriction:start", "join:start", "join:stop", "restriction:stop"), events);
	}

	@Test
	void cleansUpPartialInitializationAndKeepsOriginalFailure() {
		List<String> events = new ArrayList<>();
		var base = new TestFeature("base", Set.of(), events);
		var broken = new TestFeature("broken", Set.of("base"), events) {
			@Override public void initialize(@NotNull FeatureContext context) {
				super.initialize(context);
				throw new IllegalStateException("startup");
			}
			@Override public void shutdown(@NotNull FeatureContext context) {
				super.shutdown(context);
				throw new IllegalArgumentException("cleanup");
			}
		};
		try (var runtime = FeatureRuntime.prepare(path, List.of(base, broken))) {
			var failure = assertThrows(IllegalStateException.class, () -> runtime.initialize(root(runtime, new Providers())));
			assertEquals("startup", failure.getMessage());
			assertEquals(1, failure.getSuppressed().length);
		}
		assertEquals(List.of("base:start", "broken:start", "broken:stop", "base:stop"), events);
	}

	@Test
	void rejectsInvalidCompiledGraphs() {
		assertThrows(IllegalArgumentException.class, () -> FeatureRuntime.prepare(path, List.of(new TestFeature("same"), new TestFeature("same"))));
		assertThrows(IllegalArgumentException.class, () -> FeatureRuntime.prepare(path, List.of(new TestFeature("join", Set.of("missing"), new ArrayList<>()))));
		assertThrows(IllegalArgumentException.class, () -> FeatureRuntime.prepare(path, List.of(
				new TestFeature("a", Set.of("b"), new ArrayList<>()), new TestFeature("b", Set.of("a"), new ArrayList<>()))));
	}

	private Injector root(FeatureRuntime runtime, Providers settings) {
		List<Module> modules = new ArrayList<>(runtime.modules());
		modules.add(binder -> binder.bind(Providers.class).toInstance(settings));
		return Guice.createInjector(modules);
	}

	private void support(FeatureRuntime runtime, String id, String... features) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setSupportedFeatureIds(List.of(features));
		runtime.providerModules(id, path, descriptor);
	}

	private Providers.ProviderEntry provider(String id, Boolean enabled) {
		var entry = new Providers.ProviderEntry();
		entry.setId(id);
		if (enabled != null) {
			var features = new Providers.ProviderEntry.Features();
			features.put("recognition", JsonNodeFactory.instance.objectNode().put("enabled", enabled));
			entry.setFeatures(features);
		}
		return entry;
	}

	private static class TestFeature implements IdenticaFeature {
		private final String id;
		private final Set<String> requires;
		private final List<String> events;
		private final AtomicBoolean enabled = new AtomicBoolean(true);
		private TestFeature(String id) { this(id, Set.of(), new ArrayList<>()); }
		private TestFeature(String id, Set<String> requires, List<String> events) {
			this.id = id; this.requires = requires; this.events = events;
		}
		@Override public @NotNull String id() { return id; }
		@Override public @NotNull Set<String> requiredFeatures() { return requires; }
		@Override public @NotNull List<Module> modules(@NotNull FeatureContext context) { return List.of(); }
		@Override public boolean enabledByDefault(@NotNull FeatureContext context) { return enabled.get(); }
		@Override public void initialize(@NotNull FeatureContext context) { events.add(id + ":start"); }
		@Override public void shutdown(@NotNull FeatureContext context) { events.add(id + ":stop"); }
	}
}
