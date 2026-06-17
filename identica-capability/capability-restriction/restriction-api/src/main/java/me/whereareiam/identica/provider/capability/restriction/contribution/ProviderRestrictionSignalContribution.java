package me.whereareiam.identica.provider.capability.restriction.contribution;

import me.whereareiam.identica.provider.capability.contribution.ProviderCapabilityContribution;
import me.whereareiam.identica.provider.capability.restriction.model.RestrictionEvaluationRequest;
import me.whereareiam.identica.provider.capability.restriction.type.RestrictionSignal;
import me.whereareiam.identica.provider.capability.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

/**
 * Provider-local matcher for a restriction signal owned by a capability or
 * provider module.
 */
public interface ProviderRestrictionSignalContribution extends ProviderCapabilityContribution {
	/**
	 * Returns the restriction type this snapshot supports.
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
