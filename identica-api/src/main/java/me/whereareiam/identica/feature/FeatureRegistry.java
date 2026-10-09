package me.whereareiam.identica.feature;

import com.google.inject.Module;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Access to compiled feature services and their per-provider configuration.
 */
public interface FeatureRegistry {
	/**
	 * Checks whether an implementation is available, ignoring surrounding whitespace and case.
	 *
	 * @param id feature identifier
	 * @return whether the feature is compiled into Identica
	 */
	boolean isAvailable(@NotNull String id);

	/**
	 * Checks a provider's feature enable switch after runtime initialization.
	 * Missing switches inherit the feature's own policy defaults. This does not
	 * grant support to providers that do not declare the feature.
	 *
	 * @param providerId provider whose configuration is consulted
	 * @param featureId feature identifier
	 * @return false when explicitly disabled or unavailable
	 */
	boolean isEnabled(@NotNull String providerId, @NotNull String featureId);

	/**
	 * Returns compiled IDs in dependency order, with dependencies before consumers.
	 *
	 * @return immutable ordered set of normalized feature IDs
	 */
	@NotNull Set<String> ids();

	/**
	 * Collects provider-local modules from compiled features advertised by the
	 * provider descriptor, in feature dependency order.
	 *
	 * @param context context for the provider being composed
	 * @return immutable list of participating modules
	 */
	@NotNull List<Module> providerModules(@NotNull FeatureProviderContext context);
}
