package me.whereareiam.identica.feature.restriction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Resolved restriction decision for a provider restriction evaluation.
 */
@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true)
public class RestrictionDecision {
	private final @NotNull RestrictionType type;
	private final @Nullable String providerId;
	private final boolean allowed;
	private final boolean configured;
	private final boolean active;
	private final @NotNull Set<RestrictionSignal> allow;
	private final @NotNull Set<RestrictionSignal> matchedSignals;

	/**
	 * Creates a resolved restriction decision for a provider evaluation.
	 *
	 * @param type restriction type
	 * @param providerId provider id
	 * @param allowed whether the evaluation allows the operation
	 * @param configured whether the restriction is configured for the provider
	 * @param active whether the restriction is currently active
	 * @param allow allowed signals configured for the restriction
	 * @param matchedSignals signals that matched during evaluation
	 * @return resolved restriction decision
	 */
	public static @NotNull RestrictionDecision of(
			@NotNull RestrictionType type,
			@Nullable String providerId,
			boolean allowed,
			boolean configured,
			boolean active,
			@NotNull Set<RestrictionSignal> allow,
			@NotNull Set<RestrictionSignal> matchedSignals
	) {
		return RestrictionDecision.builder()
				.type(type)
				.providerId(providerId)
				.allowed(allowed)
				.configured(configured)
				.active(active)
				.allow(allow)
				.matchedSignals(matchedSignals)
				.build();
	}
}
