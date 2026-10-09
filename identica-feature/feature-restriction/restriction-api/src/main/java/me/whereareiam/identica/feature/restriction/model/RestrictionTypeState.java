package me.whereareiam.identica.feature.restriction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Resolved provider state for a restriction type.
 */
@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true)
public class RestrictionTypeState {
	private final @NotNull String providerId;
	private final boolean enabled;
	private final boolean configured;
	private final @NotNull Set<RestrictionSignal> allow;
}
