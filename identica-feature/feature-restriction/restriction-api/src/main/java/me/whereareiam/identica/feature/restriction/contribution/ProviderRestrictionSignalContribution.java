package me.whereareiam.identica.feature.restriction.contribution;

import me.whereareiam.identica.feature.ProviderFeatureContribution;
import me.whereareiam.identica.feature.restriction.model.RestrictionEvaluationRequest;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

/**
 * Provider-local matcher for a restriction signal owned by a feature or
 * provider module.
 */
public interface ProviderRestrictionSignalContribution extends ProviderFeatureContribution {
	/**
	 * Returns the restriction type this contribution supports.
	 *
	 * @return supported restriction type
	 */
	@NotNull RestrictionType restrictionType();

	/**
	 * Returns the signal contributed for the restriction type.
	 *
	 * @return contributed signal id
	 */
	@NotNull RestrictionSignal signal();

	/**
	 * Determines whether the signal matches the supplied request.
	 *
	 * @param request evaluation request
	 * @return {@code true} when the signal matches
	 */
	boolean matches(@NotNull RestrictionEvaluationRequest request);
}
