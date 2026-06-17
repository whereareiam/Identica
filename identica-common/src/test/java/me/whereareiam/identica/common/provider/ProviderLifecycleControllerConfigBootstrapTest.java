package me.whereareiam.identica.common.provider;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.provider.capability.DefaultProviderCapabilityCoordinator;
import me.whereareiam.identica.common.provider.capability.DefaultProviderCapabilityRegistry;
import me.whereareiam.identica.common.provider.classloader.ProviderRuntimeClassLoaderFactory;
import me.whereareiam.identica.common.provider.classloader.SharedCapabilityClassLoaderFactory;
import me.whereareiam.identica.common.provider.dependency.ProviderDependencyLoggingAdapter;
import me.whereareiam.identica.common.provider.factory.ProviderInstanceFactory;
import me.whereareiam.identica.common.provider.injector.ProviderInjectorFactory;
import me.whereareiam.identica.common.provider.library.ProviderLibraryInstaller;
import me.whereareiam.identica.common.provider.library.ProviderLibraryPlanner;
import me.whereareiam.identica.common.provider.library.SharedLibraryConflictTracker;
import me.whereareiam.identica.common.provider.resolver.ProviderResolverRegistry;
import me.whereareiam.identica.common.provider.resolver.ProviderWorkingPathResolver;
import me.whereareiam.identica.common.registry.ReloadableRegistry;
import me.whereareiam.identica.common.replication.store.DefaultScopedParticipantRegistry;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.conflict.ConflictType;
import me.whereareiam.identica.conflict.resolver.ConflictResolver;
import me.whereareiam.identica.database.schema.SchemaBootstrap;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.ProviderPlatformBinding;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.capability.ProviderCapabilityRegistry;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import me.whereareiam.identica.type.event.EventOrder;
import me.whereareiam.identica.type.provider.ProviderState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Provider Lifecycle Config Bootstrap")
class ProviderLifecycleControllerConfigBootstrapTest {
	@DisplayName("Loading a provider prewarms provider-local configs")
	@Test
	void loadingAProviderPrewarmsProviderLocalConfigs(@TempDir Path tempDir) {
		Injector injector = Guice.createInjector(new ProviderLifecycleTestModule(tempDir));
		ProviderLifecycleController controller = injector.getInstance(ProviderLifecycleController.class);
		InternalProvider provider = discoveredProvider();

		controller.loadProvider(provider);

		assertEquals(ProviderState.LOADED, provider.getState());
		assertTrue(Files.exists(tempDir.resolve("providers").resolve("Test Provider").resolve("settings.yml")));
		assertTrue(Files.exists(tempDir.resolve("providers").resolve("Test Provider").resolve("messages.yml")));
		assertTrue(Files.exists(tempDir.resolve("providers").resolve("Test Provider").resolve("commands.yml")));
	}

	@DisplayName("Provider configs are not created until provider load runs")
	@Test
	void providerConfigsAreNotCreatedUntilProviderLoadRuns(@TempDir Path tempDir) {
		assertFalse(Files.exists(tempDir.resolve("providers").resolve("Disabled Provider").resolve("settings.yml")));
	}

	@DisplayName("Enabling and disabling a provider registers platform bindings")
	@Test
	void enablingAndDisablingAProviderRegistersPlatformBindings(@TempDir Path tempDir) {
		ProbePlatformBinding.reset();
		Injector injector = Guice.createInjector(new ProviderLifecycleTestModule(tempDir));
		ProviderLifecycleController controller = injector.getInstance(ProviderLifecycleController.class);
		InternalProvider provider = discoveredProvider();

		controller.loadProvider(provider);
		controller.enableProvider(provider);
		assertEquals(1, ProbePlatformBinding.registerCount());

		controller.disableProvider(provider);
		assertEquals(1, ProbePlatformBinding.unregisterCount());
	}

	@DisplayName("Enabling and disabling a provider registers and unregisters boundary participants")
	@Test
	void enablingAndDisablingAProviderRegistersAndUnregistersBoundaryParticipants(@TempDir Path tempDir) {
		Injector injector = Guice.createInjector(new ProviderLifecycleTestModule(tempDir));
		ProviderLifecycleController controller = injector.getInstance(ProviderLifecycleController.class);
		InternalProvider provider = discoveredProvider();

		assertTrue(injector.getInstance(Key.get(new TypeLiteral<Registry<ConnectionDisconnectedParticipant>>() {})).values().isEmpty());
		assertTrue(injector.getInstance(Key.get(new TypeLiteral<Registry<AccountLifecycleParticipant>>() {})).values().isEmpty());

		controller.loadProvider(provider);
		controller.enableProvider(provider);

		assertEquals(1, injector.getInstance(Key.get(new TypeLiteral<Registry<ConnectionDisconnectedParticipant>>() {})).values().size());
		assertEquals(1, injector.getInstance(Key.get(new TypeLiteral<Registry<AccountLifecycleParticipant>>() {})).values().size());

		controller.disableProvider(provider);

		assertTrue(injector.getInstance(Key.get(new TypeLiteral<Registry<ConnectionDisconnectedParticipant>>() {})).values().isEmpty());
		assertTrue(injector.getInstance(Key.get(new TypeLiteral<Registry<AccountLifecycleParticipant>>() {})).values().isEmpty());
	}

	@DisplayName("Boundary participants are unregistered even when provider disable fails")
	@Test
	void boundaryParticipantsAreUnregisteredWhenProviderDisableFails(@TempDir Path tempDir) {
		Injector injector = Guice.createInjector(new ProviderLifecycleTestModule(tempDir));
		ProviderLifecycleController controller = injector.getInstance(ProviderLifecycleController.class);
		InternalProvider provider = discoveredProvider(ThrowingDisableProvider.class.getName());

		controller.loadProvider(provider);
		controller.enableProvider(provider);
		controller.disableProvider(provider);

		assertEquals(ProviderState.FAILED, provider.getState());
		assertTrue(injector.getInstance(Key.get(new TypeLiteral<Registry<ConnectionDisconnectedParticipant>>() {})).values().isEmpty());
		assertTrue(injector.getInstance(Key.get(new TypeLiteral<Registry<AccountLifecycleParticipant>>() {})).values().isEmpty());
	}

	private static InternalProvider discoveredProvider() {
		return discoveredProvider(TestProvider.class.getName());
	}

	private static InternalProvider discoveredProvider(String mainClass) {
		return InternalProvider.builder()
					.path(Path.of("ignored.jar"))
					.descriptor(descriptor(mainClass))
					.state(ProviderState.DISCOVERED)
					.build();
	}

	private static ProviderDescriptor descriptor() {
		return descriptor(TestProvider.class.getName());
	}

	private static ProviderDescriptor descriptor(String mainClass) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("test-provider");
		descriptor.setName("Test Provider");
		descriptor.setVersion("1.0.0");
		descriptor.setMain(mainClass);
		descriptor.setSupportedPlatforms(List.of("any"));
		descriptor.setLibraries(ProviderLibraries.empty());
		return descriptor;
	}

	private static final class ProviderLifecycleTestModule extends AbstractModule {
		private final Path tempDir;

		private ProviderLifecycleTestModule(Path tempDir) {
			this.tempDir = tempDir;
		}

			@Override
			protected void configure() {
				bind(ProviderWorkingPathResolver.class).asEagerSingleton();
				bind(ProviderInstanceFactory.class).asEagerSingleton();
				bind(ProviderLifecycleController.class).asEagerSingleton();
			bind(ProviderCapabilityCoordinator.class).to(DefaultProviderCapabilityCoordinator.class).asEagerSingleton();
			bind(ProviderCapabilityRegistry.class).to(DefaultProviderCapabilityRegistry.class).asEagerSingleton();
			bind(new TypeLiteral<Registry<Reloadable>>() {}).to(ReloadableRegistry.class).asEagerSingleton();
			bind(ConflictService.class).toInstance(new NoopConflictService());
				bind(EventManager.class).toInstance(new NoopEventManager());
				bind(HandshakeStore.class).toInstance(new NoopHandshakeStore());
				bind(SchemaBootstrap.class).toInstance(contributor -> {});
				bind(ProviderResolverRegistry.class).toInstance(new ProviderResolverRegistry());
				bind(DefaultScopedParticipantRegistry.class).asEagerSingleton();
			}

		@Provides
		@Singleton
		@Named("providersPath")
		Path provideProvidersPath() throws Exception {
			Path providersPath = tempDir.resolve("providers");
			Files.createDirectories(providersPath);
			return providersPath;
		}

		@Provides
		@Singleton
		ProviderRuntimeClassLoaderFactory provideProviderRuntimeClassLoaderFactory() {
			return new ProviderRuntimeClassLoaderFactory(new SharedCapabilityClassLoaderFactory()) {
				@Override
				public URLClassLoader create(Path jarPath) {
					return new URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
				}
			};
		}

		@Provides
		@Singleton
		ProviderLibraryInstaller provideProviderLibraryInstaller() {
			return new ProviderLibraryInstaller(
					tempDir.resolve("providers"),
					tempDir.resolve("capabilities"),
					org.mockito.Mockito.mock(ProviderDependencyLoggingAdapter.class),
					new SharedCapabilityClassLoaderFactory()
			) {
				@Override
				protected void install(
						@NotNull Path basePath,
						@NotNull String cacheNamespace,
						ProviderLibraries libraries,
						@NotNull URLClassLoader classLoader
				) {
				}
			};
		}

		@Provides
		@Singleton
		ProviderLibraryPlanner provideProviderLibraryPlanner() {
			return new ProviderLibraryPlanner(new SharedLibraryConflictTracker());
		}

			@Provides
			@Singleton
	        ProviderInjectorFactory provideInjectorFactory() {
				return new ProviderInjectorFactory(null) {
				@Override
				public Injector create(
						Path workingPath,
						ProviderDescriptor descriptor,
						IdenticaProvider probeProvider,
						ProviderPlatformExtension probePlatformExtension,
						@NotNull List<Module> capabilityModules
					) {
						return Guice.createInjector(new ProviderRootModule(), new ProviderRuntimeModule(workingPath, probeProvider));
					}
				};
			}

		@Provides
		@Singleton
		Registry<ConnectionDisconnectedParticipant> provideConnectionDisconnectedParticipants(DefaultScopedParticipantRegistry registry) {
			return registry.disconnected();
		}

		@Provides
		@Singleton
		Registry<AccountLifecycleParticipant> provideAccountLifecycleParticipants(DefaultScopedParticipantRegistry registry) {
			return registry.accounts();
		}

		@Provides
		@Singleton
		Registry<ConnectionCompletedParticipant> provideConnectionCompletedParticipants(DefaultScopedParticipantRegistry registry) {
			return registry.completed();
		}

		@Provides
			@Singleton
			Registry<ConnectionTerminatedParticipant> provideConnectionTerminatedParticipants(DefaultScopedParticipantRegistry registry) {
				return registry.terminated();
			}
		}

	private static final class ProviderRootModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(new TypeLiteral<Registry<Reloadable>>() {}).to(ReloadableRegistry.class).asEagerSingleton();
		}
	}

	private static final class ProviderRuntimeModule extends AbstractModule {
		private final Path workingPath;
		private final IdenticaProvider probeProvider;

		private ProviderRuntimeModule(Path workingPath, IdenticaProvider probeProvider) {
			this.workingPath = workingPath;
			this.probeProvider = probeProvider;
		}

		@Override
		protected void configure() {
			if (probeProvider != null)
				for (Module module : probeProvider.modules())
					install(module);
		}

		@Provides
		@Singleton
		@Named("workingPath")
		Path provideWorkingPath() {
			return workingPath;
		}

		@Provides
		@Singleton
		ProviderDescriptor provideDescriptor() {
			ProviderDescriptor descriptor = new ProviderDescriptor();
			descriptor.setId("test-provider");
			descriptor.setName("Test Provider");
			descriptor.setVersion("1.0.0");
			descriptor.setMain(TestProvider.class.getName());
			descriptor.setSupportedPlatforms(List.of("any"));
			descriptor.setLibraries(ProviderLibraries.empty());
			return descriptor;
		}
	}

	public static class TestProvider extends IdenticaProvider {
		@Override
		public @NotNull List<Module> modules() {
			return List.of(new TestProviderModule());
		}
	}

	public static class ThrowingDisableProvider extends TestProvider {
		@Override
		public void onDisable() {
			throw new IllegalStateException("boom");
		}
	}

	private static final class TestProviderModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(TestSettingsProvider.class).asEagerSingleton();
			bind(TestMessagesProvider.class).asEagerSingleton();
			bind(TestCommandsProvider.class).asEagerSingleton();
			Multibinder.newSetBinder(binder(), ProviderPlatformBinding.class)
					.addBinding()
					.to(ProbePlatformBinding.class);
			Multibinder.newSetBinder(binder(), ConnectionDisconnectedParticipant.class)
					.addBinding()
					.to(ProbeConnectionDisconnectedParticipant.class);
			Multibinder.newSetBinder(binder(), AccountLifecycleParticipant.class)
					.addBinding()
					.to(ProbeAccountLifecycleParticipant.class);
		}
	}

	private static final class ProbePlatformBinding implements ProviderPlatformBinding {
		private static final AtomicInteger REGISTER_COUNT = new AtomicInteger();
		private static final AtomicInteger UNREGISTER_COUNT = new AtomicInteger();

		@Override
		public void register() {
			REGISTER_COUNT.incrementAndGet();
		}

		@Override
		public void unregister() {
			UNREGISTER_COUNT.incrementAndGet();
		}

		private static void reset() {
			REGISTER_COUNT.set(0);
			UNREGISTER_COUNT.set(0);
		}

		private static int registerCount() {
			return REGISTER_COUNT.get();
		}

		private static int unregisterCount() {
			return UNREGISTER_COUNT.get();
		}
	}

	private static final class ProbeConnectionDisconnectedParticipant implements ConnectionDisconnectedParticipant {
		@Override
		public void onConnectionDisconnected(@NotNull me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent event) {
		}
	}

	private static final class ProbeAccountLifecycleParticipant implements AccountLifecycleParticipant {
		@Override
		public void onAccountLifecycle(@NotNull me.whereareiam.identica.event.account.AccountLifecycleEvent event) {
		}
	}

	@Singleton
	private static final class TestSettingsProvider extends ConfigProvider<TestDocument> {
		@Inject
		private TestSettingsProvider(@Named("workingPath") Path workingPath, Registry<Reloadable> registry) {
			super(workingPath, "settings", TestDocument.class, registry);
		}

		@Override
		protected Configura configura() {
			return versioned(Config.configured().withDefaults(TestDefaults.class), TestDocument.class);
		}
	}

	@Singleton
	private static final class TestMessagesProvider extends ConfigProvider<TestDocument> {
		@Inject
		private TestMessagesProvider(@Named("workingPath") Path workingPath, Registry<Reloadable> registry) {
			super(workingPath, "messages", TestDocument.class, registry);
		}

		@Override
		protected Configura configura() {
			return versioned(Config.configured().withDefaults(TestDefaults.class), TestDocument.class);
		}
	}

	@Singleton
	private static final class TestCommandsProvider extends ConfigProvider<TestDocument> {
		@Inject
		private TestCommandsProvider(@Named("workingPath") Path workingPath, Registry<Reloadable> registry) {
			super(workingPath, "commands", TestDocument.class, registry);
		}

		@Override
		protected Configura configura() {
			return versioned(Config.configured().withDefaults(TestDefaults.class), TestDocument.class);
		}
	}

	@Setter
    @Getter
    public static class TestDocument {
		private String value;
    }

	@Singleton
	public static class TestDefaults implements DefaultsProvider<TestDocument> {
		@Override
		public TestDocument supply(TestDocument config) {
			config.setValue("prepared");
			return config;
		}
	}

	private static final class NoopConflictService implements ConflictService {
		@Override
		public void register(@NotNull ConflictResolver resolver) {
		}

		@Override
		public void unregister(@NotNull ConflictResolver resolver) {
		}

		@Override
		public ConflictResolver getResolver(@NotNull String id) {
			return null;
		}

		@Override
		public void register(@NotNull ConflictType<?> type) {
		}

		@Override
		public void unregister(@NotNull ConflictType<?> type) {
		}

		@Override
		public ConflictType<?> getType(@NotNull String key) {
			return null;
		}

		@Override
		public @NotNull Set<ConflictType<?>> getTypes() {
			return Set.of();
		}

		@Override
		public ConflictResolution resolve(@NotNull ConflictContext context) {
			return null;
		}
	}

	private static final class NoopEventManager implements EventManager {
		@Override
		public void register(EventListener eventListener) {
		}

		@Override
		public <T extends Event> void registerListener(Class<T> event, Object listener, Method method, EventOrder order) {
		}

		@Override
		public void unregister(EventListener eventListener) {
		}

		@Override
		public void call(Event event) {
		}
	}

	private static final class NoopHandshakeStore implements HandshakeStore {
		@Override
		public void registerPolicy(@NotNull HandshakePolicy policy) {
		}

		@Override
		public void unregisterPolicy(@NotNull HandshakePolicy policy) {
		}

		@Override
		public @NotNull Set<HandshakePolicy> policies() {
			return Set.of();
		}

		@Override
		public void putInstruction(@NotNull HandshakeInstruction instruction) {
		}

		@Override
		public @NotNull Optional<HandshakeInstruction> consumeInstruction(@NotNull String username, @NotNull String ip) {
			return Optional.empty();
		}

		@Override
		public void invalidateInstruction(@NotNull String username, @NotNull String ip) {
		}
	}
}
