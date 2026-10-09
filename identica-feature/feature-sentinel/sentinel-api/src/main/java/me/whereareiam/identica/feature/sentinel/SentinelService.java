package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Evaluates registered sentinel policies and manages their attempt counters.
 * This service exists only while the sentinel feature is installed.
 */
public interface SentinelService {
	/**
	 * Evaluates policies for an operation, recording attempts for record-mode policies.
	 * @param scope connection operation
	 * @param context connection and account identifiers
	 * @return strongest limited decision, or empty when no policy limits the operation
	 */
	@NotNull Optional<SentinelDecision> evaluate(@NotNull SentinelScope scope, @NotNull SentinelContext context);

	/**
	 * Records one attempt for a registered policy.
	 * @param id policy identifier
	 * @param context connection and account identifiers
	 * @return decision; an unknown or inactive policy allows the attempt
	 */
	@NotNull SentinelDecision record(@NotNull String id, @NotNull SentinelContext context);

	/**
	 * Removes the attempts and lockout for one policy and counter identity.
	 * @param id policy identifier
	 * @param context connection and account identifiers
	 */
	void clear(@NotNull String id, @NotNull SentinelContext context);
}
