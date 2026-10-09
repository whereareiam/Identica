package me.whereareiam.identica.platform.velocity.feature;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.common.config.ConfigBindings;
import me.whereareiam.identica.common.config.ConfigInitializer;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.common.registry.ReloadableRegistry;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.database.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.database.schema.SchemaBootstrap;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.feature.BuiltinFeatures;
import me.whereareiam.identica.trait.authoritative.username.UsernameConfiguration;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Composes real compiled features; only services at the core/IO boundary are stubbed. */
class BundledFeatureCompositionTest {
	private static final Map<String, String> SERVICES = Map.of(
			"verification", "verification.VerificationService",
			"recognition", "recognition.SessionRecognitionService",
			"restriction", "restriction.RestrictionService",
			"restriction-join", "restriction.join.JoinRestrictionTypeResolver",
			"sentinel", "sentinel.SentinelService"
	);
	private static final Map<String, String> CONFIG_FILES = Map.of(
			"verification", "verification/settings.yml",
			"recognition", "recognition/settings.yml",
			"restriction", "restriction/settings.yml",
			"restriction-join", "restriction/join/commands.yml",
			"sentinel", "sentinel/settings.yml"
	);

	@TempDir
	Path dataPath;
	private Configura previousConfiguration;
	private final ReloadableRegistry reloadables = new ReloadableRegistry();
	private final Set<EventListener> listeners = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<String, PipelineExtension> pipelineExtensions = new HashMap<>();
	private final Set<String> commandIds = new HashSet<>();
	private final Set<String> suggestionIds = new HashSet<>();
	private final EventManager events = mock(EventManager.class);
	private final PipelineExtensionRegistry pipelines = mock(PipelineExtensionRegistry.class);
	private final CommandService commands = mock(CommandService.class);
	private final SchemaBootstrap schema = mock(SchemaBootstrap.class);

	@BeforeEach
	void configureRealDocumentsAndBoundaryRegistrations() {
		previousConfiguration = Config.configured();
		Config.setConfigured(Config.builder().format(Format.YAML).module(new IdenticaModule()).build());
		doAnswer(invocation -> {
			assertTrue(listeners.add(invocation.getArgument(0)), "Listener registered twice");
			return null;
		}).when(events).register(any(EventListener.class));
		doAnswer(invocation -> {
			assertTrue(listeners.remove(invocation.getArgument(0)), "Unregistering an unowned listener");
			return null;
		}).when(events).unregister(any(EventListener.class));
		doAnswer(invocation -> {
			PipelineExtension extension = invocation.getArgument(0);
			assertNull(pipelineExtensions.putIfAbsent(extension.id(), extension), "Duplicate pipeline extension ID");
			return null;
		}).when(pipelines).register(any(PipelineExtension.class));
		doAnswer(invocation -> {
			assertNotNull(pipelineExtensions.remove(invocation.getArgument(0)), "Unregistering an unowned extension");
			return true;
		}).when(pipelines).unregister(anyString());
		doAnswer(invocation -> {
			Map<String, CommandDefinition> definitions = invocation.getArgument(0);
			assertFalse(definitions.isEmpty(), "Real command defaults must be populated");
			for (String id : definitions.keySet())
				assertTrue(commandIds.add(id), "Duplicate command definition: " + id);
			return null;
		}).when(commands).registerCommandInstances(anyMap(), any(Object[].class));
		doAnswer(invocation -> {
			Set<String> ids = invocation.getArgument(0);
			for (String id : ids)
				assertTrue(commandIds.remove(id), "Unregistering an unowned command: " + id);
			return null;
		}).when(commands).unregisterCommands(anySet());
		doAnswer(invocation -> {
			assertTrue(suggestionIds.add(invocation.getArgument(0)), "Duplicate suggestion key");
			return null;
		}).when(commands).registerSuggestionProvider(anyString(), any());
		doAnswer(invocation -> {
			assertTrue(suggestionIds.remove(invocation.getArgument(0)), "Unregistering an unowned suggestion");
			return null;
		}).when(commands).unregisterSuggestionProvider(anyString());
	}

	@AfterEach
	void restoreConfiguration() {
		Config.setConfigured(previousConfiguration);
	}

	static java.util.stream.Stream<Integer> providerSelections() {
		return java.util.stream.IntStream.range(0, 1 << SERVICES.size()).boxed();
	}

	@ParameterizedTest(name = "independent provider feature choices: {0}")
	@MethodSource("providerSelections")
	void composesEveryFeatureAndAppliesIndependentProviderConfiguration(int mask) throws Exception {
		List<String> ids = SERVICES.keySet().stream().sorted().toList();
		Files.createDirectories(dataPath.resolve("providers"));
		StringBuilder yaml = new StringBuilder("providers:\n");
		for (int provider = 0; provider < 2; provider++) {
			yaml.append("  - id: ").append(provider == 0 ? "first" : "second").append("\n    features:\n");
			for (int index = 0; index < ids.size(); index++) {
				String id = ids.get(index);
				if (id.equals("restriction-join")) continue;
				boolean enabled = ((mask & (1 << index)) != 0) == (provider == 0);
				yaml.append("      ").append(id).append(":\n        enabled: ").append(enabled).append('\n');
				if (id.equals("restriction")) {
					boolean join = ((mask & (1 << ids.indexOf("restriction-join"))) != 0) == (provider == 0);
					yaml.append("        join:\n          enabled: ").append(join).append('\n');
				}
			}
			yaml.append("      custom-extension:\n        retained: custom-value\n");
		}
		Files.writeString(dataPath.resolve("providers/providers.yml"), yaml);
		try (FeatureRuntime runtime = BuiltinFeatures.runtime(dataPath)) {
			List<Module> modules = new ArrayList<>(runtime.modules());
			modules.add(new UsernameConfiguration(dataPath.resolve("identity/username")));
			modules.add(new CoreBoundaries());
			modules.add(new ConfigBindings());
			Injector root = Guice.createInjector(Stage.PRODUCTION, modules);
			ConfigInitializer.initialize(root);
			var lifecycles = root.getInstance(Key.get(new TypeLiteral<Set<RuntimeLifecycle>>() {}));
			lifecycles.forEach(RuntimeLifecycle::initialize);
			runtime.initialize(root);
			assertEquals(SERVICES.keySet(), runtime.ids());
			assertFalse(runtime.isAvailable("username"));
			for (String provider : List.of("first", "second")) {
				ProviderDescriptor descriptor = new ProviderDescriptor();
				descriptor.setId(provider);
				descriptor.setSupportedFeatureIds(ids);
				runtime.providerModules(provider, dataPath, descriptor);
				for (int index = 0; index < ids.size(); index++) {
					String id = ids.get(index);
					boolean enabled = ((mask & (1 << index)) != 0) == provider.equals("first");
					if (id.equals("restriction-join"))
						enabled &= ((mask & (1 << ids.indexOf("restriction"))) != 0) == provider.equals("first");
					assertEquals(enabled, runtime.isEnabled(provider, id), provider + ": " + id);
				}
			}
			for (var service : SERVICES.entrySet()) {
				Class<?> type = getClass().getClassLoader().loadClass("me.whereareiam.identica.feature." + service.getValue());
				var binding = root.getExistingBinding(Key.get(type));
				assertNotNull(binding);
				assertFalse(mockingDetails(binding.getProvider().get()).isMock());
				assertTrue(Files.exists(dataPath.resolve("features").resolve(CONFIG_FILES.get(service.getKey()))));
			}
			assertTrue(Files.exists(dataPath.resolve("identity/username/messages.yml")));
			verify(schema, times(2)).apply(any());
			var providerNodes = Config.configured().readNode(dataPath.resolve("providers/providers.yml")).get("providers");
			assertEquals("custom-value", providerNodes.get(0).path("features").path("custom-extension").path("retained").asText());
			runtime.shutdown();
			lifecycles.forEach(RuntimeLifecycle::shutdown);
			assertAll(
					() -> assertTrue(listeners.isEmpty()),
					() -> assertTrue(pipelineExtensions.isEmpty()),
					() -> assertTrue(commandIds.isEmpty()),
					() -> assertTrue(suggestionIds.isEmpty())
			);
		}
	}

	private final class CoreBoundaries extends AbstractModule {
		@Override
		protected void configure() {
			bind(Path.class).annotatedWith(Names.named("dataPath")).toInstance(dataPath);
			bind(Path.class).annotatedWith(Names.named("providersPath")).toInstance(dataPath.resolve("providers"));
			bind(new TypeLiteral<Registry<Reloadable>>() {}).toInstance(reloadables);
			bind(EventManager.class).toInstance(events);
			bind(PipelineExtensionRegistry.class).toInstance(pipelines);
			bind(CommandService.class).toInstance(commands);
			bind(SchemaBootstrap.class).toInstance(schema);
			bind(ProviderManager.class).toInstance(mock(ProviderManager.class));
			bind(ProviderOperations.class).toInstance(mock(ProviderOperations.class));
			bind(SessionService.class).toInstance(mock(SessionService.class));
			bind(IdentityService.class).toInstance(mock(IdentityService.class));
			bind(ConflictService.class).toInstance(mock(ConflictService.class));
			bind(AccountPersistenceService.class).toInstance(mock(AccountPersistenceService.class));
			bind(ProviderLinkPersistenceService.class).toInstance(mock(ProviderLinkPersistenceService.class));
			bind(ProviderProfilePersistenceService.class).toInstance(mock(ProviderProfilePersistenceService.class));
			bind(PipelineStateStore.class).toInstance(mock(PipelineStateStore.class));
			bind(ConnectionCoordinator.class).toInstance(mock(ConnectionCoordinator.class));
			bind(ReplicationSystem.class).toInstance(emptyReplication());
			Jdbi jdbi = mock(Jdbi.class);
			// Keep feature persistence services real; stub only generated SQL repository interfaces.
			when(jdbi.onDemand(any())).thenAnswer(invocation -> mock((Class<?>) invocation.getArgument(0)));
			bind(Jdbi.class).toInstance(jdbi);
		}
	}

	private static ReplicationSystem emptyReplication() {
		ReplicationSystem system = mock(ReplicationSystem.class);
		ReplicationCacheBuilder builder = mock(ReplicationCacheBuilder.class, RETURNS_SELF);
		LocalCache<Object> local = mock(LocalCache.class);
		ReplicatedCache<Object> replicated = mock(ReplicatedCache.class);
		when(local.listKeys(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(ReplicationPage.empty(1, 100)));
		when(replicated.listKeys(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(ReplicationPage.empty(1, 100)));
		when(system.cache(anyString())).thenReturn(builder);
		when(builder.local()).thenReturn(local);
		when(builder.replicated(any())).thenReturn(replicated);
		return system;
	}
}
