package me.whereareiam.identica.common.provider.capability;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.name.Names;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.provider.runtime.classloader.SharedCapabilityClassLoaderFactory;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.capability.ProviderCapabilityDeclaration;
import me.whereareiam.identica.model.provider.capability.ProviderCapabilityInstallation;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.capability.ProviderCapabilityRegistry;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityBootstrap;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityGlobalInstallContext;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityInitializationContext;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityLocalInstallContext;
import me.whereareiam.identica.provider.capability.contribution.ProviderCapabilityContribution;
import me.whereareiam.identica.type.provider.capability.ProviderCapability;
import me.whereareiam.identica.type.provider.capability.ProviderCapabilityScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultProviderCapabilityCoordinator implements ProviderCapabilityCoordinator {
	private static final TypeLiteral<Set<ProviderCapabilityContribution>> CAPABILITY_CONTRIBUTIONS = new TypeLiteral<>() {};

	private final Injector rootInjector;
	private final ProviderCapabilityRegistry capabilityRegistry;
	private final SharedCapabilityClassLoaderFactory sharedCapabilityClassLoaderFactory;

	@Override
	public @NotNull List<ProviderCapabilityBootstrap> resolveBootstraps(
			@NotNull ProviderDescriptor descriptor,
			@Nullable List<ProviderCapabilityBootstrap> bootstraps
	) {
		List<ProviderCapabilityBootstrap> resolvedBootstraps = new ArrayList<>();
		List<String> resolvedDeclaredCapabilityIds = new ArrayList<>();
		for (ProviderCapabilityBootstrap bootstrap : bootstraps != null ? bootstraps : List.<ProviderCapabilityBootstrap>of()) {
			if (bootstrap == null) continue;

			ProviderCapabilityDeclaration declaration = bootstrap.declaration();
			String capabilityId = declaration.getCapability().getId();
			if (resolvedDeclaredCapabilityIds.contains(capabilityId))
				throw new IllegalStateException("Provider declared duplicate capability bootstrap: " + capabilityId);

			resolvedBootstraps.add(bootstrap);
			resolvedDeclaredCapabilityIds.add(capabilityId);
		}

		descriptor.setDeclaredCapabilityIds(List.copyOf(resolvedDeclaredCapabilityIds));
		return List.copyOf(resolvedBootstraps);
	}

	@Override
	public void installGlobalCapabilities(
			@NotNull InternalProvider provider,
			@NotNull List<ProviderCapabilityBootstrap> bootstraps
	) {
		for (ProviderCapabilityBootstrap bootstrap : bootstraps) {
			if (bootstrap.declaration().getScopes().contains(ProviderCapabilityScope.GLOBAL))
				installGlobalCapability(provider, bootstrap);
		}
	}

	@Override
	public @NotNull List<Module> resolveLocalModules(
			@NotNull InternalProvider provider,
			@NotNull List<ProviderCapabilityBootstrap> bootstraps
	) {
		ProviderDescriptor descriptor = provider.getDescriptor();
		if (descriptor == null || provider.getWorkingPath() == null) return List.of();

		List<Module> modules = new ArrayList<>();
		List<Injector> globalInjectors = new ArrayList<>();
		for (ProviderCapabilityBootstrap bootstrap : bootstraps) {
			ProviderCapabilityDeclaration capabilityDeclaration = bootstrap.declaration();
			ProviderCapabilityInstallation installation = capabilityRegistry.findInstallation(capabilityDeclaration.getCapability());

			if (installation != null && installation.getGlobalInjector() != null)
				globalInjectors.add(installation.getGlobalInjector());

			if (!capabilityDeclaration.getScopes().contains(ProviderCapabilityScope.LOCAL)) continue;
			modules.addAll(resolveLocalModules(bootstrap, capabilityDeclaration.getCapability(), descriptor.getId(), provider.getWorkingPath()));
		}
		if (!globalInjectors.isEmpty())
			modules.addFirst(new CapabilityGlobalBridgeModule(rootInjector, sharedCapabilityClassLoaderFactory.sharedClassLoader(), globalInjectors));

		return List.copyOf(modules);
	}

	@Override
	public @NotNull Set<ProviderCapabilityContribution> resolveCapabilityContributions(@NotNull Injector injector) {
		try {
			Set<ProviderCapabilityContribution> resolved = injector.getInstance(Key.get(CAPABILITY_CONTRIBUTIONS));
			if (resolved == null || resolved.isEmpty()) return Set.of();

			return Set.copyOf(resolved);
		} catch (ConfigurationException ignored) {
			return Set.of();
		}
	}

	@Override
	public void validateCapabilityContributions(
			@NotNull ProviderDescriptor descriptor,
			@NotNull List<ProviderCapabilityBootstrap> bootstraps,
			@NotNull Set<ProviderCapabilityContribution> contributions
	) {
		Set<String> declaredCapabilityIds = new LinkedHashSet<>();
		for (ProviderCapabilityBootstrap bootstrap : bootstraps)
			declaredCapabilityIds.add(bootstrap.declaration().getCapability().getId());

		Map<String, Integer> contributionCounts = countContributions(declaredCapabilityIds, contributions);

		for (ProviderCapabilityBootstrap bootstrap : bootstraps) {
			ProviderCapabilityDeclaration capabilityDeclaration = bootstrap.declaration();
			if (!capabilityDeclaration.isRequiresContribution()) continue;

			String capabilityId = capabilityDeclaration.getCapability().getId();
			if (contributionCounts.getOrDefault(capabilityId, 0) <= 0)
				throw new IllegalStateException("Provider is missing required capability snapshot: " + capabilityId);
		}
	}

	private @NotNull Map<String, Integer> countContributions(
			@NotNull Set<String> declaredCapabilityIds,
			@NotNull Set<ProviderCapabilityContribution> contributions
	) {
		Map<String, Integer> contributionCounts = new LinkedHashMap<>();
		for (ProviderCapabilityContribution contribution : contributions) {
			if (contribution == null) continue;

			String capabilityId = contribution.capability().getId();
			if (!declaredCapabilityIds.contains(capabilityId))
				throw new IllegalStateException("Provider exposed undeclared capability snapshot: " + capabilityId);

			contributionCounts.merge(capabilityId, 1, Integer::sum);
		}

		return contributionCounts;
	}

	private @NotNull List<Module> resolveLocalModules(
			@NotNull ProviderCapabilityBootstrap bootstrap,
			@NotNull ProviderCapability capability,
			@NotNull String providerId,
			@NotNull Path workingPath
	) {
		return bootstrap.localModules(ProviderCapabilityLocalInstallContext.builder()
				.capability(capability)
				.providerId(providerId)
				.workingPath(workingPath)
				.build());
	}

	private void installGlobalCapability(@NotNull InternalProvider provider, @NotNull ProviderCapabilityBootstrap bootstrap) {
		ProviderCapability capability = bootstrap.declaration().getCapability();
		ProviderCapabilityInstallation installation = capabilityRegistry.findInstallation(capability);
		if (installation != null) {
			validateCompatibleBootstrap(capability, installation.getBootstrap(), bootstrap);
			return;
		}

		List<Module> modules = bootstrap.globalModules(ProviderCapabilityGlobalInstallContext.builder()
				.capability(capability)
				.rootInjector(rootInjector)
				.capabilitiesPath(rootInjector.getInstance(Key.get(Path.class, Names.named("capabilitiesPath"))))
				.build());
		List<Module> injectorModules = new ArrayList<>(resolveGlobalBridgeModules());
		injectorModules.addAll(modules);
		Injector capabilityInjector = injectorModules.isEmpty() ? null : rootInjector.createChildInjector(injectorModules);
		bootstrap.initialize(ProviderCapabilityInitializationContext.builder()
				.capability(capability)
				.rootInjector(rootInjector)
				.globalInjector(capabilityInjector)
				.build());
		capabilityRegistry.registerInstallation(ProviderCapabilityInstallation.builder()
				.bootstrap(bootstrap)
				.globalInjector(capabilityInjector)
				.build());

		Logger.debug("Installed provider capability %s for provider %s", capability.getId(), safeId(provider));
	}

	private void validateCompatibleBootstrap(
			@NotNull ProviderCapability capability,
			@NotNull ProviderCapabilityBootstrap existing,
			@NotNull ProviderCapabilityBootstrap requested
	) {
		if (existing.getClass().getName().equals(requested.getClass().getName())
				&& existing.declaration().getScopes().equals(requested.declaration().getScopes())
				&& existing.declaration().isRequiresContribution() == requested.declaration().isRequiresContribution())
			return;

		throw new IllegalStateException("Capability bootstrap conflict for " + capability.getId()
				+ ": " + existing.getClass().getName()
				+ " != " + requested.getClass().getName());
	}

	private @NotNull String safeId(@NotNull InternalProvider provider) {
		ProviderDescriptor descriptor = provider.getDescriptor();
		return descriptor != null ? safeId(descriptor) : "unknown";
	}

	private @NotNull String safeId(@NotNull ProviderDescriptor descriptor) {
		if (descriptor.getId().isBlank()) return "unknown";

		return descriptor.getId();
	}
	private @NotNull List<Module> resolveGlobalBridgeModules() {
		List<Injector> globalInjectors = new ArrayList<>();
		for (ProviderCapabilityInstallation installation : capabilityRegistry.installations()) {
			if (installation == null || installation.getGlobalInjector() == null) continue;

			globalInjectors.add(installation.getGlobalInjector());
		}
		if (globalInjectors.isEmpty()) return List.of();

		return List.of(new CapabilityGlobalBridgeModule(rootInjector, sharedCapabilityClassLoaderFactory.sharedClassLoader(), globalInjectors));
	}

	private static final class CapabilityGlobalBridgeModule extends AbstractModule {
		private final Injector parentInjector;
		private final ClassLoader sharedCapabilityApiClassLoader;
		private final List<Injector> globalInjectors;
		private final ClassLoader parentClassLoader;

		private CapabilityGlobalBridgeModule(
				@NotNull Injector parentInjector,
				@NotNull ClassLoader sharedCapabilityApiClassLoader,
				@NotNull List<Injector> globalInjectors
		) {
			this.parentInjector = parentInjector;
			this.sharedCapabilityApiClassLoader = sharedCapabilityApiClassLoader;
			this.globalInjectors = List.copyOf(globalInjectors);
			this.parentClassLoader = parentInjector.getClass().getClassLoader();
		}

		@Override
		protected void configure() {
			Set<Key<?>> seen = new HashSet<>();
			for (Injector globalInjector : globalInjectors) {
				for (Key<?> key : globalInjector.getBindings().keySet()) {
					if (!seen.add(key) || shouldSkip(key)) continue;

					bindBridge(globalInjector, key);
				}
			}
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private <T> void bindBridge(@NotNull Injector globalInjector, @NotNull Key<T> key) {
			bind((Key) key).toProvider(globalInjector.getProvider(key));
		}

		private boolean shouldSkip(@NotNull Key<?> key) {
			Class<?> rawType = key.getTypeLiteral().getRawType();
			return rawType == Injector.class
					|| rawType.getName().startsWith("com.google.inject.")
					|| isProviderLocalType(rawType)
					|| parentInjector.getExistingBinding(key) != null;
		}

		private boolean isProviderLocalType(@NotNull Class<?> rawType) {
			ClassLoader classLoader = rawType.getClassLoader();
			if (classLoader == null) return false;

			return classLoader != parentClassLoader
					&& classLoader != parentClassLoader.getParent()
					&& classLoader != sharedCapabilityApiClassLoader;
		}
	}
}
