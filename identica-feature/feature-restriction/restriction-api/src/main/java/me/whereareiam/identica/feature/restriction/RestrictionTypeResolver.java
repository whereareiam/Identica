package me.whereareiam.identica.feature.restriction;

import me.whereareiam.identica.feature.restriction.model.RestrictionTypeState;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Extension point that resolves configured state for a restriction type.
 */
public interface RestrictionTypeResolver {
	/**
	 * Returns the restriction type handled by this resolver.
	 *
	 * @return handled restriction type
	 */
	@NotNull RestrictionType type();

	/**
	 * Resolves state for a single provider.
	 *
	 * @param providerId provider id
	 * @return resolved state or empty when the provider is unknown for this type
	 */
	@NotNull Optional<RestrictionTypeState> resolve(@Nullable String providerId);

	/**
	 * Resolves state for every provider supported by this type.
	 *
	 * @return resolved provider states
	 */
	@NotNull List<RestrictionTypeState> resolveAll();
}
