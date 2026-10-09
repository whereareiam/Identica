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
 * Resolved status of a provider restriction type.
 */
@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true)
public class RestrictionStatus {
	private final @NotNull RestrictionType type;
	private final @Nullable String providerId;
	private final boolean active;
	private final boolean configured;
	private final @NotNull Set<RestrictionSignal> allow;

	/**
	 * Creates a resolved status for a provider restriction type.
	 *
	 * @param type restriction type
	 * @param providerId provider id
	 * @param active whether the restriction is currently active
	 * @param configured whether the restriction is configured for the provider
	 * @param allow allowed signals configured for the restriction
	 * @return resolved restriction status
	 */
	public static @NotNull RestrictionStatus of(
			@NotNull RestrictionType type,
			@Nullable String providerId,
			boolean active,
			boolean configured,
			@NotNull Set<RestrictionSignal> allow
	) {
		return RestrictionStatus.builder()
				.type(type)
				.providerId(providerId)
				.active(active)
				.configured(configured)
				.allow(allow)
				.build();
	}
}
