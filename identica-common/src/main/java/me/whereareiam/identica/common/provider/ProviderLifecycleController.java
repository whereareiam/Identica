package me.whereareiam.identica.common.provider;

import com.google.inject.*;
import com.google.inject.Module;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.config.ConfigInitializer;
import me.whereareiam.identica.common.provider.classloader.ProviderRuntimeClassLoaderFactory;
import me.whereareiam.identica.common.provider.factory.ProviderInstanceFactory;
import me.whereareiam.identica.common.provider.injector.ProviderInjectorFactory;
import me.whereareiam.identica.common.provider.library.ProviderLibraryInstaller;
import me.whereareiam.identica.common.provider.library.ProviderLibraryPlanner;
import me.whereareiam.identica.common.provider.resolver.ProviderPlatformExtensionResolver;
import me.whereareiam.identica.common.provider.resolver.ProviderResolverRegistry;
import me.whereareiam.identica.common.provider.resolver.ProviderWorkingPathResolver;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.database.schema.SchemaBootstrap;
import me.whereareiam.identica.database.schema.SchemaContributor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.provider.state.ProviderDisabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderEnabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderLoadedEvent;
import me.whereareiam.identica.event.provider.state.ProviderUnloadedEvent;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.ProviderPlatformBinding;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityBootstrap;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.resolver.ProviderResolver;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import me.whereareiam.identica.type.provider.ProviderFeature;
import me.whereareiam.identica.type.provider.ProviderState;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO Rewrite

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderLifecycleController {
	private static final TypeLiteral<Set<HandshakePolicy>> HANDSHAKE_POLICIES = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderEligibilityResolver>> ELIGIBILITY_RESOLVERS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<SubjectResolver>> SUBJECT_RESOLVERS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderMigrationPrecheck>> MIGRATION_PRECHECKS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<SchemaContributor>> SCHEMA_CONTRIBUTORS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderPlatformBinding>> PLATFORM_BINDINGS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ConnectionDisconnectedParticipant>> CONNECTION_DISCONNECTED_PARTICIPANTS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ConnectionCompletedParticipant>> CONNECTION_COMPLETED_PARTICIPANTS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ConnectionTerminatedParticipant>> CONNECTION_TERMINATED_PARTICIPANTS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<AccountLifecycleParticipant>> ACCOUNT_LIFECYCLE_PARTICIPANTS = new TypeLiteral<>() {};

	private final ProviderWorkingPathResolver workingPathResolver;
	private final ProviderRuntimeClassLoaderFactory providerRuntimeClassLoaderFactory;
	private final ProviderLibraryPlanner providerLibraryPlanner;
	private final ProviderLibraryInstaller providerLibraryInstaller;
	private final ProviderInjectorFactory injectorFactory;
	private final ProviderInstanceFactory instanceFactory;
	private final ProviderPlatformExtensionResolver platformExtensionResolver;
	private final ProviderResolverRegistry resolverRegistry;
	private final ProviderCapabilityCoordinator capabilityCoordinator;
	private final ConflictService conflictService;
	private final SchemaBootstrap schemaBootstrap;
	private final EventManager eventManager;
	private final HandshakeStore handshakeStore;
	private final Registry<ConnectionDisconnectedParticipant> connectionDisconnectedParticipants;
	private final Registry<ConnectionCompletedParticipant> connectionCompletedParticipants;
	private final Registry<ConnectionTerminatedParticipant> connectionTerminatedParticipants;
	private final Registry<AccountLifecycleParticipant> accountLifecycleParticipants;

	private final ConcurrentHashMap<InternalProvider, Set<HandshakePolicy>> providerHandshakePolicies = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<InternalProvider, Set<ProviderPlatformBinding>> providerPlatformBindings = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<InternalProvider, Set<ConnectionDisconnectedParticipant>> providerConnectionDisconnectedParticipants = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<InternalProvider, Set<ConnectionCompletedParticipant>> providerConnectionCompletedParticipants = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<InternalProvider, Set<ConnectionTerminatedParticipant>> providerConnectionTerminatedParticipants = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<InternalProvider, Set<AccountLifecycleParticipant>> providerAccountLifecycleParticipants = new ConcurrentHashMap<>();

	public void loadProvider(InternalProvider internal) {
		if (internal == null || internal.getState() != ProviderState.DISCOVERED) return;
		Logger.debug("Loading provider %s", safeId(internal));

		URLClassLoader classLoader = null;
		try {
			ProviderDescriptor descriptor = internal.getDescriptor();
			Path workingPath = workingPathResolver.resolve(descriptor);
			classLoader = providerRuntimeClassLoaderFactory.create(internal.getPath());

			Class<?> providerClass = classLoader.loadClass(descriptor.getMain());
			if (!IdenticaProvider.class.isAssignableFrom(providerClass)) {
				Logger.warn("Provider main class does not extend IdenticaProvider: %s", descriptor.getId());
				internal.setState(ProviderState.FAILED);
				providerRuntimeClassLoaderFactory.close(classLoader);

				return;
			}

			IdenticaProvider probeProvider = instanceFactory.instantiateProvider(providerClass);
			if (probeProvider != null) {
				probeProvider.setDescriptor(descriptor);
				probeProvider.setWorkingPath(workingPath);
			}

			ProviderLibraries libraries = probeProvider != null
					? probeProvider.libraries()
					: ProviderLibraries.empty();

			ProviderLibraryPlanner.ProviderLibraryPlan libraryPlan = providerLibraryPlanner.plan(libraries);
			providerLibraryInstaller.installSharedCapabilityApis(libraryPlan.sharedCapabilityApis());
			providerLibraryInstaller.installProviderRuntime(descriptor, libraryPlan.providerRuntimeLibraries(), classLoader);
			Class<? extends ProviderPlatformExtension> platformExtensionClass = platformExtensionResolver.resolve(probeProvider);
			ProviderPlatformExtension probePlatformExtension = platformExtensionClass != null
					? instanceFactory.instantiatePlatformExtension(platformExtensionClass)
					: null;

			List<ProviderCapabilityBootstrap> capabilityBootstraps = capabilityCoordinator.resolveBootstraps(
					descriptor,
					probeProvider != null ? probeProvider.declaredCapabilities() : List.of()
			);
			descriptor.setDeclaredFeatureIds(probeProvider != null
					? probeProvider.declaredFeatures().stream()
							.filter(feature -> feature != null && !feature.getId().isBlank())
							.map(ProviderFeature::getId)
							.map(id -> id.trim().toLowerCase(Locale.ROOT))
							.distinct()
							.toList()
					: List.of());
			internal.setWorkingPath(workingPath);
			capabilityCoordinator.installGlobalCapabilities(internal, capabilityBootstraps);
			List<Module> capabilityModules = capabilityCoordinator.resolveLocalModules(internal, capabilityBootstraps);

			Injector providerInjector = injectorFactory.create(
					workingPath,
					descriptor,
					probeProvider,
					probePlatformExtension,
					capabilityModules
			);

			applySchemaContributors(providerInjector);
			IdenticaProvider provider = instanceFactory.createInjectedProvider(
					providerInjector,
					providerClass,
					probeProvider
			);
			if (provider == null) {
				internal.setState(ProviderState.FAILED);
				providerRuntimeClassLoaderFactory.close(classLoader);
				return;
			}

			provider.setDescriptor(descriptor);
			provider.setWorkingPath(workingPath);
			if (platformExtensionClass != null) {
				ProviderPlatformExtension platformExtension = instanceFactory.createInjectedPlatformExtension(
						providerInjector,
						platformExtensionClass,
						probePlatformExtension
				);
				provider.setPlatformExtension(platformExtension);
			}

			internal.setProvider(provider);
			internal.setWorkingPath(workingPath);
			internal.setClassLoader(classLoader);
			prewarmProviderConfigs(providerInjector, internal);
			storeBindings(internal, providerInjector);
			capabilityCoordinator.validateCapabilityContributions(
					descriptor,
					capabilityBootstraps,
					internal.getCapabilityContributions() != null ? internal.getCapabilityContributions() : Set.of()
			);
			internal.setState(ProviderState.LOADED);
			if (checkRequirements(internal))
				return;

			provider.onLoad();
			fireProviderLoaded(internal);
		} catch (Exception e) {
			if (classLoader != null && internal.getClassLoader() != classLoader)
				providerRuntimeClassLoaderFactory.close(classLoader);

			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to load provider %s: %s", safeId(internal), e.getMessage());
		}
	}

	public void enableProvider(InternalProvider internal) {
		if (internal == null || internal.getState() != ProviderState.LOADED) return;
		if (internal.getProvider() == null) return;

		Logger.debug("Enabling provider %s", safeId(internal));
		internal.setState(ProviderState.ENABLED);
		try {
			registerConflictResolvers(internal.getProvider());
			internal.getProvider().onEnable();
			registerProviderBindings(internal);
			fireProviderEnabled(internal);
		} catch (Exception e) {
			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to enable provider %s: %s", safeId(internal), e.getMessage());
		}
	}

	public void disableProvider(InternalProvider internal) {
		if (internal == null || internal.getState() != ProviderState.ENABLED) return;
		if (internal.getProvider() == null) return;

		Logger.debug("Disabling provider %s", safeId(internal));
		internal.setState(ProviderState.DISABLED);
		try {
			unregisterConflictResolvers(internal.getProvider());
			internal.getProvider().onDisable();
			fireProviderDisabled(internal);
		} catch (Exception e) {
			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to disable provider %s: %s", safeId(internal), e.getMessage());
			fireProviderDisabled(internal);
		} finally {
			unregisterProviderBindings(internal);
		}
	}

	public void unloadProvider(InternalProvider internal) {
		if (internal == null || internal.getState() != ProviderState.DISABLED) return;
		if (internal.getProvider() == null) return;

		Logger.debug("Unloading provider %s", safeId(internal));
		try {
			internal.getProvider().onUnload();
			internal.setState(ProviderState.UNLOADED);
			fireProviderUnloaded(internal);
		} catch (Exception e) {
			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to unload provider %s: %s", safeId(internal), e.getMessage());
			fireProviderUnloaded(internal);
		} finally {
			providerRuntimeClassLoaderFactory.close(internal.getClassLoader());
		}
	}

	private boolean checkRequirements(InternalProvider provider) {
		for (ProviderResolver resolver : resolverRegistry.getAll()) {
			if (!resolver.resolve(provider)) {
				provider.setState(ProviderState.FAILED);
				return true;
			}
		}

		return false;
	}

	private void registerConflictResolvers(IdenticaProvider provider) {
		if (provider == null) return;
		for (var resolver : provider.getConflictResolvers())
			conflictService.register(resolver);

		for (var type : provider.getConflictTypes())
			conflictService.register(type);
	}

	private void unregisterConflictResolvers(IdenticaProvider provider) {
		if (provider == null) return;
		for (var resolver : provider.getConflictResolvers())
			conflictService.unregister(resolver);

		for (var type : provider.getConflictTypes())
			conflictService.unregister(type);

	}

	private void storeBindings(InternalProvider internal, Injector injector) {
		if (internal == null || injector == null) return;
		providerHandshakePolicies.put(internal, copySet(resolveSet(injector, HANDSHAKE_POLICIES)));
		providerPlatformBindings.put(internal, copySet(resolveSet(injector, PLATFORM_BINDINGS)));
		providerConnectionDisconnectedParticipants.put(internal, copyOrderedSet(resolveSet(injector, CONNECTION_DISCONNECTED_PARTICIPANTS)));
		providerConnectionCompletedParticipants.put(internal, copyOrderedSet(resolveSet(injector, CONNECTION_COMPLETED_PARTICIPANTS)));
		providerConnectionTerminatedParticipants.put(internal, copyOrderedSet(resolveSet(injector, CONNECTION_TERMINATED_PARTICIPANTS)));
		providerAccountLifecycleParticipants.put(internal, copyOrderedSet(resolveSet(injector, ACCOUNT_LIFECYCLE_PARTICIPANTS)));
		internal.setEligibilityResolvers(copySet(resolveSet(injector, ELIGIBILITY_RESOLVERS)));
		internal.setSubjectResolvers(copySet(resolveSet(injector, SUBJECT_RESOLVERS)));
		internal.setMigrationPrechecks(copySet(resolveSet(injector, MIGRATION_PRECHECKS)));
		internal.setCapabilityContributions(copySet(capabilityCoordinator.resolveCapabilityContributions(injector)));
	}

	private void registerProviderBindings(InternalProvider internal) {
		if (internal == null) return;
		Set<ConnectionDisconnectedParticipant> disconnectedParticipants = providerConnectionDisconnectedParticipants.get(internal);
		if (disconnectedParticipants != null)
			for (ConnectionDisconnectedParticipant participant : disconnectedParticipants)
				connectionDisconnectedParticipants.register(participant);

		Set<ConnectionCompletedParticipant> completedParticipants = providerConnectionCompletedParticipants.get(internal);
		if (completedParticipants != null)
			for (ConnectionCompletedParticipant participant : completedParticipants)
				connectionCompletedParticipants.register(participant);

		Set<ConnectionTerminatedParticipant> terminatedParticipants = providerConnectionTerminatedParticipants.get(internal);
		if (terminatedParticipants != null)
			for (ConnectionTerminatedParticipant participant : terminatedParticipants)
				connectionTerminatedParticipants.register(participant);

		Set<AccountLifecycleParticipant> accountParticipants = providerAccountLifecycleParticipants.get(internal);
		if (accountParticipants != null)
			for (AccountLifecycleParticipant participant : accountParticipants)
				accountLifecycleParticipants.register(participant);

		Set<HandshakePolicy> policies = providerHandshakePolicies.get(internal);
		if (policies != null)
			for (HandshakePolicy policy : policies)
				handshakeStore.registerPolicy(policy);

		Set<ProviderPlatformBinding> platformBindings = providerPlatformBindings.get(internal);
		if (platformBindings != null)
			for (ProviderPlatformBinding binding : platformBindings)
				binding.register();
	}

	private void applySchemaContributors(Injector injector) {
		for (SchemaContributor contributor : resolveSet(injector, SCHEMA_CONTRIBUTORS))
			schemaBootstrap.apply(contributor);
	}

	private void unregisterProviderBindings(InternalProvider internal) {
		if (internal == null) return;

		Set<ConnectionDisconnectedParticipant> disconnectedParticipants = providerConnectionDisconnectedParticipants.remove(internal);
		if (disconnectedParticipants != null)
			for (ConnectionDisconnectedParticipant participant : disconnectedParticipants)
				connectionDisconnectedParticipants.unregister(participant);

		Set<ConnectionCompletedParticipant> completedParticipants = providerConnectionCompletedParticipants.remove(internal);
		if (completedParticipants != null)
			for (ConnectionCompletedParticipant participant : completedParticipants)
				connectionCompletedParticipants.unregister(participant);

		Set<ConnectionTerminatedParticipant> terminatedParticipants = providerConnectionTerminatedParticipants.remove(internal);
		if (terminatedParticipants != null)
			for (ConnectionTerminatedParticipant participant : terminatedParticipants)
				connectionTerminatedParticipants.unregister(participant);

		Set<AccountLifecycleParticipant> accountParticipants = providerAccountLifecycleParticipants.remove(internal);
		if (accountParticipants != null)
			for (AccountLifecycleParticipant participant : accountParticipants)
				accountLifecycleParticipants.unregister(participant);

		Set<ProviderPlatformBinding> platformBindings = providerPlatformBindings.remove(internal);
		if (platformBindings != null)
			for (ProviderPlatformBinding binding : platformBindings)
				binding.unregister();

		Set<HandshakePolicy> policies = providerHandshakePolicies.remove(internal);
		if (policies != null)
			for (HandshakePolicy policy : policies)
				handshakeStore.unregisterPolicy(policy);
	}

	private <T> Set<T> resolveSet(Injector injector, TypeLiteral<Set<T>> type) {
		try {
			Set<T> resolved = injector.getInstance(com.google.inject.Key.get(type));
			return resolved != null ? resolved : Set.of();
		} catch (com.google.inject.ConfigurationException ignored) {
			return Set.of();
		}
	}

	private <T> Set<T> copySet(Set<T> values) {
		if (values == null || values.isEmpty())
			return Set.of();

		return Set.copyOf(values);
	}

	private <T> Set<T> copyOrderedSet(Set<T> values) {
		if (values == null || values.isEmpty())
			return Set.of();

		return Collections.unmodifiableSet(new LinkedHashSet<>(values));
	}

	private void fireProviderDisabled(InternalProvider internal) {
		if (eventManager == null || internal == null) return;
		eventManager.call(new ProviderDisabledEvent(internal));
	}

	private void fireProviderLoaded(InternalProvider internal) {
		if (eventManager == null || internal == null) return;
		eventManager.call(new ProviderLoadedEvent(internal));
	}

	private void fireProviderEnabled(InternalProvider internal) {
		if (eventManager == null || internal == null) return;
		eventManager.call(new ProviderEnabledEvent(internal));
	}

	private void fireProviderUnloaded(InternalProvider internal) {
		if (eventManager == null || internal == null) return;
		eventManager.call(new ProviderUnloadedEvent(internal));
	}

	private void prewarmProviderConfigs(Injector providerInjector, InternalProvider internal) {
		List<String> prepared = ConfigInitializer.initialize(
				providerInjector,
				type -> type.getSimpleName().endsWith("Provider")
		);

		if (!prepared.isEmpty())
			Logger.info("Prepared provider configs for %s: %s", safeId(internal), String.join(", ", prepared));
	}

	private String safeId(InternalProvider internal) {
		if (internal == null || internal.getDescriptor() == null)
			return "unknown";

		String id = internal.getDescriptor().getId();
		return !id.isBlank() ? id : "unknown";
	}
}
