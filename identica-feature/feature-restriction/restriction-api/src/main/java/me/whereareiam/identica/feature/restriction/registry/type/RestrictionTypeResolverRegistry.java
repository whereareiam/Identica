package me.whereareiam.identica.feature.restriction.registry.type;

import me.whereareiam.identica.feature.restriction.RestrictionTypeResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Registry of available restriction type handlers.
 */
public interface RestrictionTypeResolverRegistry {
	/**
	 * Registers a handler for a restriction type.
	 *
	 * @param handler handler to register
	 */
	void register(@NotNull RestrictionTypeResolver handler);

	/**
	 * Unregisters a handler for a restriction type.
	 *
	 * @param handler handler to unregister
	 */
	void unregister(@NotNull RestrictionTypeResolver handler);

	/**
	 * Finds the handler for a restriction type id.
	 *
	 * @param typeId restriction type id
	 * @return resolved handler or empty when missing
	 */
	@NotNull Optional<RestrictionTypeResolver> find(@NotNull String typeId);

	/**
	 * Returns all registered handlers.
	 *
	 * @return registered handlers
	 */
	@NotNull List<RestrictionTypeResolver> values();
}
