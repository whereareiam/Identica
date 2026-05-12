package me.whereareiam.identica.common.provider;

import com.google.inject.*;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.config.ConfigInitializer;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.common.provider.dependency.ProviderDependencyResolver;
import me.whereareiam.identica.common.provider.factory.ProviderClassLoaderFactory;
import me.whereareiam.identica.common.provider.factory.ProviderInstanceFactory;
import me.whereareiam.identica.common.provider.injector.ProviderInjectorFactory;
import me.whereareiam.identica.common.provider.resolver.ProviderResolverRegistry;
import me.whereareiam.identica.common.provider.resolver.ProviderWorkingPathResolver;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.provider.state.ProviderDisabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderEnabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderLoadedEvent;
import me.whereareiam.identica.event.provider.state.ProviderUnloadedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.profile.ProfileSubjectResolver;
import me.whereareiam.identica.provider.resolver.ProviderResolver;
import me.whereareiam.identica.type.provider.ProviderState;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderLifecycleController {
	private static final TypeLiteral<Set<HandshakePolicy>> HANDSHAKE_POLICIES = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderEligibilityResolver>> ELIGIBILITY_RESOLVERS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProfileSubjectResolver>> PROFILE_RESOLVERS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderMigrationPrecheck>> MIGRATION_PRECHECKS = new TypeLiteral<>() {};

	private final ProviderWorkingPathResolver workingPathResolver;
	private final ProviderClassLoaderFactory classLoaderFactory;
	private final ProviderDependencyResolver dependencyResolver;
	private final ProviderInjectorFactory injectorFactory;
	private final ProviderInstanceFactory instanceFactory;
	private final ProviderResolverRegistry resolverRegistry;
	private final ConflictService conflictService;
	private final EventManager eventManager;
	private final HandshakeStore handshakeStore;

	private final ConcurrentHashMap<InternalProvider, Set<HandshakePolicy>> providerHandshakePolicies = new ConcurrentHashMap<>();

	public void loadProvider(InternalProvider internal) {
		if (internal == null || internal.getState() != ProviderState.DISCOVERED) return;
		Logger.debug("Loading provider %s", safeId(internal));

		try {
			ProviderDescriptor descriptor = internal.getDescriptor();
			Path workingPath = workingPathResolver.resolve(descriptor);
			URLClassLoader classLoader = classLoaderFactory.create(internal.getPath());

			dependencyResolver.loadDescriptorLibraries(descriptor, classLoader);

			Class<?> providerClass = classLoader.loadClass(descriptor.getMain());
			if (!IdenticaProvider.class.isAssignableFrom(providerClass)) {
				Logger.warn("Provider main class does not extend IdenticaProvider: %s", descriptor.getId());
				internal.setState(ProviderState.FAILED);
				classLoaderFactory.close(classLoader);

				return;
			}

			IdenticaProvider probeProvider = instanceFactory.instantiateProvider(providerClass);
			if (probeProvider != null) {
				probeProvider.setDescriptor(descriptor);
				probeProvider.setWorkingPath(workingPath);
			}

			dependencyResolver.loadProviderLibraries(descriptor, probeProvider, classLoader);

			Injector providerInjector = injectorFactory.create(workingPath, descriptor, probeProvider);
			IdenticaProvider provider = instanceFactory.createInjectedProvider(
					providerInjector,
					providerClass,
					probeProvider
			);
			if (provider == null) {
				internal.setState(ProviderState.FAILED);
				classLoaderFactory.close(classLoader);
				return;
			}

			provider.setDescriptor(descriptor);
			provider.setWorkingPath(workingPath);

			internal.setProvider(provider);
			internal.setWorkingPath(workingPath);
			internal.setClassLoader(classLoader);
			prewarmProviderConfigs(providerInjector, internal);
			storeBindings(internal, providerInjector);
			internal.setState(ProviderState.LOADED);
			if (checkRequirements(internal))
				return;

			provider.onLoad();
			fireProviderLoaded(internal);
		} catch (Exception e) {
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
			classLoaderFactory.close(internal.getClassLoader());
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
		internal.setEligibilityResolvers(copySet(resolveSet(injector, ELIGIBILITY_RESOLVERS)));
		internal.setProfileSubjectResolvers(copySet(resolveSet(injector, PROFILE_RESOLVERS)));
		internal.setMigrationPrechecks(copySet(resolveSet(injector, MIGRATION_PRECHECKS)));
	}

	private void registerProviderBindings(InternalProvider internal) {
		if (internal == null) return;
		Set<HandshakePolicy> policies = providerHandshakePolicies.get(internal);
		if (policies != null)
			for (HandshakePolicy policy : policies)
				handshakeStore.registerPolicy(policy);
	}

	private void unregisterProviderBindings(InternalProvider internal) {
		if (internal == null) return;
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
