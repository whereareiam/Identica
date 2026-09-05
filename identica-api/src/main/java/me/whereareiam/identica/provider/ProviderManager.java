package me.whereareiam.identica.provider;

import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.provider.resolver.ProviderResolver;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manages provider discovery and lifecycle operations.
 */
@SuppressWarnings("unused")
public interface ProviderManager {
	/**
	 * Discovers and loads enabled providers.
	 */
	void loadProviders();

	/**
	 * Disables and unloads all loaded providers.
	 */
	void unloadProviders();

	/**
	 * Returns the currently managed providers.
	 *
	 * @return immutable view of managed providers
	 */
	List<InternalProvider> getProviders();

	/**
	 * Finds providers that advertise all requested traits.
	 *
	 * @param traits required traits
	 * @return immutable list of matching providers ordered by priority
	 */
	@NotNull List<InternalProvider> findProvidersByTraits(@NotNull ProviderTrait... traits);

	/**
	 * Finds the highest priority provider that advertises all requested traits.
	 *
	 * @param traits required traits
	 * @return matching provider or {@code null} when none match
	 */
	@Nullable InternalProvider findProviderByTraits(@NotNull ProviderTrait... traits);

	/**
	 * Finds providers that support all requested features.
	 *
	 * @param features supported integrations to match
	 * @return immutable list of matching providers ordered by priority
	 */
	@NotNull List<InternalProvider> findProvidersByFeatures(@NotNull String... features);

	/**
	 * Finds the highest priority provider that supports all requested features.
	 *
	 * @param features supported integrations to match
	 * @return matching provider or {@code null} when none match
	 */
	@Nullable InternalProvider findProviderByFeatures(@NotNull String... features);

	/**
	 * Registers a resolver used during provider loading.
	 */
	void registerResolver(ProviderResolver resolver);

	/**
	 * Removes a resolver used during provider loading.
	 */
	void unregisterResolver(ProviderResolver resolver);

	/**
	 * Returns the currently registered resolvers.
	 *
	 * @return immutable view of resolvers
	 */
	List<ProviderResolver> getResolvers();
}
