package me.whereareiam.identica.common.provider;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.common.provider.resolver.ProviderPlatformResolver;
import me.whereareiam.identica.common.provider.resolver.ProviderResolverRegistry;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.resolver.ProviderResolver;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class DefaultProviderManager implements ProviderManager {
	private final ProviderDiscovery discovery;
	private final ProviderLifecycleController lifecycleController;
	private final Provider<Providers> providersConfig;
	private final ProviderResolverRegistry resolverRegistry;

	private final List<InternalProvider> providers = new ArrayList<>();

	@Inject
	public DefaultProviderManager(
			ProviderDiscovery discovery,
			ProviderLifecycleController lifecycleController,
			Provider<Providers> providersConfig,
			ProviderResolverRegistry resolverRegistry,
			ProviderPlatformResolver platformResolver
	) {
		this.discovery = discovery;
		this.lifecycleController = lifecycleController;
		this.providersConfig = providersConfig;
		this.resolverRegistry = resolverRegistry;

		registerResolver(platformResolver);
	}

	@Override
	public void loadProviders() {
		providers.clear();
		Logger.debug("Discovering providers...");
		List<InternalProvider> discovered = discovery.discover(providers);
		List<InternalProvider> enabledProviders = selectEnabled(discovered, providersConfig.get());

		for (InternalProvider provider : enabledProviders) {
			lifecycleController.loadProvider(provider);
			lifecycleController.enableProvider(provider);
			if (provider.getState() == ProviderState.ENABLED) {
				providers.add(provider);
			}
		}
	}

	@Override
	public void unloadProviders() {
		for (InternalProvider provider : providers) {
			lifecycleController.disableProvider(provider);
		}
		for (InternalProvider provider : providers) {
			lifecycleController.unloadProvider(provider);
		}
	}

	@Override
	public List<InternalProvider> getProviders() {
		return Collections.unmodifiableList(providers);
	}

	@Override
	public @NotNull List<InternalProvider> findProvidersByTraits(ProviderTrait... traits) {
		if (providers.isEmpty()) return List.of();

		List<InternalProvider> matches = new ArrayList<>();
		for (InternalProvider provider : providers) {
			if (provider == null || provider.getState() != ProviderState.ENABLED) continue;

			ProviderDescriptor descriptor = provider.getDescriptor();
			if (descriptor == null) continue;

			String id = descriptor.getId();
			if (id.isBlank()) continue;
			if (!supportsAll(descriptor, traits)) continue;

			matches.add(provider);
		}

		matches.sort(Comparator.comparingInt(InternalProvider::getPriority)
				.reversed()
				.thenComparing(left -> left.getDescriptor().getId(), String.CASE_INSENSITIVE_ORDER));

		return Collections.unmodifiableList(matches);
	}

	@Override
	public InternalProvider findProviderByTraits(ProviderTrait... traits) {
		List<InternalProvider> matches = findProvidersByTraits(traits);
		if (matches.isEmpty()) return null;

		return matches.getFirst();
	}

	@Override
	public @NotNull List<InternalProvider> findProvidersByFeatures(@NotNull String... features) {
		if (providers.isEmpty()) return List.of();

		List<InternalProvider> matches = new ArrayList<>();
		for (InternalProvider provider : providers) {
			if (provider == null || provider.getState() != ProviderState.ENABLED) continue;

			ProviderDescriptor descriptor = provider.getDescriptor();
			if (descriptor == null || descriptor.getId().isBlank()) continue;
			if (!supportsAll(descriptor, features)) continue;

			matches.add(provider);
		}

		matches.sort(Comparator.comparingInt(InternalProvider::getPriority)
				.reversed()
				.thenComparing(left -> left.getDescriptor().getId(), String.CASE_INSENSITIVE_ORDER));

		return Collections.unmodifiableList(matches);
	}

	@Override
	public @Nullable InternalProvider findProviderByFeatures(@NotNull String... features) {
		List<InternalProvider> matches = findProvidersByFeatures(features);
		if (matches.isEmpty()) return null;

		return matches.getFirst();
	}

	@Override
	public void registerResolver(ProviderResolver resolver) {
		resolverRegistry.register(resolver);
	}

	@Override
	public void unregisterResolver(ProviderResolver resolver) {
		resolverRegistry.unregister(resolver);
	}

	@Override
	public List<ProviderResolver> getResolvers() {
		return resolverRegistry.getAll();
	}

	private List<InternalProvider> selectEnabled(List<InternalProvider> discovered, Providers config) {
		if (config == null || config.getProviders().isEmpty()) {
			return discovered.stream()
					.peek(provider -> provider.setPriority(provider.getDescriptor().getPriority()))
					.sorted(Comparator.comparingInt(InternalProvider::getPriority).reversed())
					.collect(Collectors.toList());
		}

		Map<String, Providers.ProviderEntry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Providers.ProviderEntry entry : config.getProviders()) {
			if (entry == null || entry.getId().isBlank())
				continue;

			entries.putIfAbsent(entry.getId().trim(), entry);
		}

		return discovered.stream()
				.filter(provider -> {
					Providers.ProviderEntry entry = entries.get(provider.getDescriptor().getId());
					return entry != null && entry.isEnabled();
				})
				.peek(provider -> {
					Providers.ProviderEntry entry = entries.get(provider.getDescriptor().getId());
					int priority = entry != null ? entry.getPriority() : provider.getDescriptor().getPriority();
					provider.setPriority(priority);
				})
				.sorted(Comparator.comparingInt(InternalProvider::getPriority).reversed())
				.collect(Collectors.toList());
	}

	private boolean supportsAll(ProviderDescriptor descriptor, ProviderTrait[] traits) {
		if (traits == null) return true;

		for (ProviderTrait trait : traits) {
			if (trait == null) continue;
			if (!descriptor.hasTrait(trait))
				return false;
		}

		return true;
	}

	private boolean supportsAll(ProviderDescriptor descriptor, String[] features) {
		if (features == null) return true;

		for (String feature : features) {
			if (feature == null) continue;
			if (!descriptor.supportsFeature(feature)) return false;
		}

		return true;
	}
}
