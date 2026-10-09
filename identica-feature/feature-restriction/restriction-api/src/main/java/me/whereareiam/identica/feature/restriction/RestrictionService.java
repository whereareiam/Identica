package me.whereareiam.identica.feature.restriction;

import me.whereareiam.identica.feature.restriction.model.RestrictionDecision;
import me.whereareiam.identica.feature.restriction.model.RestrictionEvaluationRequest;
import me.whereareiam.identica.feature.restriction.model.RestrictionStatus;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Runtime manager and policy service for feature-owned restrictions.
 */
public interface RestrictionService {
	/**
	 * Enables the supplied restriction type for a provider.
	 *
	 * @param type restriction type
	 * @param providerId provider id
	 * @return resolved status after enabling
	 */
	@NotNull RestrictionStatus enable(@NotNull RestrictionType type, @Nullable String providerId);

	/**
	 * Disables the supplied restriction type for a provider.
	 *
	 * @param type restriction type
	 * @param providerId provider id
	 * @return resolved status after disabling
	 */
	@NotNull RestrictionStatus disable(@NotNull RestrictionType type, @Nullable String providerId);

	/**
	 * Returns status for a single provider restriction.
	 *
	 * @param type restriction type
	 * @param providerId provider id
	 * @return status or empty when the provider/type pair is unknown
	 */
	@NotNull Optional<RestrictionStatus> status(@NotNull RestrictionType type, @Nullable String providerId);

	/**
	 * Returns statuses for all providers supporting the supplied restriction type.
	 *
	 * @param type restriction type
	 * @return resolved statuses
	 */
	@NotNull List<RestrictionStatus> statuses(@NotNull RestrictionType type);

	/**
	 * Evaluates a restriction request.
	 *
	 * @param request evaluation request
	 * @return resolved decision
	 */
	@NotNull RestrictionDecision evaluate(@NotNull RestrictionEvaluationRequest request);
}
