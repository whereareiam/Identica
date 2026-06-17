package me.whereareiam.identica.common.provider.runtime;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.config.ConfigInitializer;
import me.whereareiam.identica.common.provider.resolver.ProviderResolverRegistry;
import me.whereareiam.identica.common.provider.runtime.binding.ProviderContributionBindersFactory;
import me.whereareiam.identica.common.provider.runtime.binding.ProviderRuntimeBindings;
import me.whereareiam.identica.common.provider.runtime.binding.snapshot.ProviderContributionSnapshot;
import me.whereareiam.identica.common.provider.runtime.binding.snapshot.ProviderContributionSnapshotFactory;
import me.whereareiam.identica.common.provider.runtime.classloader.ProviderRuntimeClassLoaderFactory;
import me.whereareiam.identica.common.provider.runtime.injector.ProviderInjectorFactory;
import me.whereareiam.identica.common.provider.runtime.library.ProviderLibraryInstaller;
import me.whereareiam.identica.common.provider.runtime.library.ProviderLibraryPlanner;
import me.whereareiam.identica.common.provider.runtime.resolver.ProviderPlatformExtensionResolver;
import me.whereareiam.identica.common.provider.runtime.resolver.ProviderWorkingPathResolver;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.database.schema.SchemaBootstrap;
import me.whereareiam.identica.database.schema.SchemaContributor;
import me.whereareiam.identica.event.provider.state.ProviderDisabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderEnabledEvent;
import me.whereareiam.identica.event.provider.state.ProviderLoadedEvent;
import me.whereareiam.identica.event.provider.state.ProviderUnloadedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityBootstrap;
import me.whereareiam.identica.provider.resolver.ProviderResolver;
import me.whereareiam.identica.type.provider.ProviderFeature;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderLifecycleController {
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
	private final ProviderContributionBindersFactory contributionBindersFactory;
	private final ProviderContributionSnapshotFactory contributionSnapshotFactory;

	private final ConcurrentHashMap<InternalProvider, ProviderRuntimeBindings> providerRuntimeBindings = new ConcurrentHashMap<>();

	public void loadProvider(InternalProvider internal) {
		if (internal == null || internal.getState() != ProviderState.DISCOVERED) return;
		Logger.debug("Loading provider %s", safeId(internal));

		URLClassLoader classLoader = null;
		try {
			ProviderDescriptor descriptor = internal.getDescriptor();
			Path providerPath = internal.getPath();
			if (descriptor == null || providerPath == null || descriptor.getMain().isBlank()) {
				internal.setState(ProviderState.FAILED);
				Logger.warn("Provider is missing required runtime metadata: %s", safeId(internal));
				return;
			}

			Path workingPath = workingPathResolver.resolve(descriptor);
			classLoader = providerRuntimeClassLoaderFactory.create(providerPath);

			Class<?> providerClass = classLoader.loadClass(descriptor.getMain());
			if (!IdenticaProvider.class.isAssignableFrom(providerClass)) {
				Logger.warn("Provider main class does not extend IdenticaProvider: %s", descriptor.getId());
				providerRuntimeClassLoaderFactory.close(classLoader);
				internal.setState(ProviderState.FAILED);
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
				providerRuntimeClassLoaderFactory.close(classLoader);
				internal.setState(ProviderState.FAILED);
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

			prewarmProviderConfigs(providerInjector, internal);
			ProviderContributionSnapshot contributionSnapshot = contributionSnapshotFactory.snapshot(providerInjector);
			capabilityCoordinator.validateCapabilityContributions(
					descriptor,
					capabilityBootstraps,
                    contributionSnapshot.getCapabilityContributions()
			);

			internal.setProvider(provider);
			internal.setWorkingPath(workingPath);
			internal.setClassLoader(classLoader);
			storeBindings(internal, contributionSnapshot);
			internal.setState(ProviderState.LOADED);
			if (checkRequirements(internal)) return;

			provider.onLoad();
			fireProviderLoaded(internal);
		} catch (Exception exception) {
			if (classLoader != null && internal.getClassLoader() != classLoader)
				providerRuntimeClassLoaderFactory.close(classLoader);

			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to load provider %s: %s", safeId(internal), exception.getMessage());
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
		} catch (Exception exception) {
			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to enable provider %s: %s", safeId(internal), exception.getMessage());
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
		} catch (Exception exception) {
			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to disable provider %s: %s", safeId(internal), exception.getMessage());
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
		} catch (Exception exception) {
			internal.setState(ProviderState.FAILED);
			Logger.warn("Failed to unload provider %s: %s", safeId(internal), exception.getMessage());
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

	private void applySchemaContributors(@NotNull Injector providerInjector) {
		for (SchemaContributor contributor : contributionSnapshotFactory.schemaContributors(providerInjector))
			schemaBootstrap.apply(contributor);
	}

	private void storeBindings(InternalProvider internal, ProviderContributionSnapshot snapshot) {
		if (internal == null || snapshot == null) return;
		providerRuntimeBindings.put(internal, snapshot.getRuntimeBindings());
		internal.setEligibilityResolvers(snapshot.getEligibilityResolvers());
		internal.setSubjectResolvers(snapshot.getSubjectResolvers());
		internal.setMigrationPrechecks(snapshot.getMigrationPrechecks());
		internal.setCapabilityContributions(snapshot.getCapabilityContributions());
	}

	private void registerProviderBindings(InternalProvider internal) {
		if (internal == null) return;
		ProviderRuntimeBindings bindings = providerRuntimeBindings.get(internal);
		if (bindings == null) return;

		bindings.bind(contributionBindersFactory.create());
	}

	private void unregisterProviderBindings(InternalProvider internal) {
		if (internal == null) return;
		ProviderRuntimeBindings bindings = providerRuntimeBindings.remove(internal);
		if (bindings == null) return;

		bindings.close();
	}

	private void fireProviderDisabled(InternalProvider internal) {
		if (internal == null) return;
		EventUtil.callEvent(new ProviderDisabledEvent(internal));
	}

	private void fireProviderLoaded(InternalProvider internal) {
		if (internal == null) return;
		EventUtil.callEvent(new ProviderLoadedEvent(internal));
	}

	private void fireProviderEnabled(InternalProvider internal) {
		if (internal == null) return;
		EventUtil.callEvent(new ProviderEnabledEvent(internal));
	}

	private void fireProviderUnloaded(InternalProvider internal) {
		if (internal == null) return;
		EventUtil.callEvent(new ProviderUnloadedEvent(internal));
	}

	private void prewarmProviderConfigs(@NotNull Injector providerInjector, @NotNull InternalProvider internal) {
		List<String> prepared = ConfigInitializer.initialize(
				providerInjector,
				type -> type.getSimpleName().endsWith("Provider")
		);

		if (!prepared.isEmpty())
			Logger.info("Prepared provider configs for %s: %s", safeId(internal), String.join(", ", prepared));
	}

	private String safeId(InternalProvider internal) {
		if (internal == null || internal.getDescriptor() == null) return "unknown";

		String id = internal.getDescriptor().getId();
		return !id.isBlank() ? id : "unknown";
	}
}
