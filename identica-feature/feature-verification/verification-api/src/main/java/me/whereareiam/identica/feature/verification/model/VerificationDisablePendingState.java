package me.whereareiam.identica.feature.verification.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * A verification method an account asked to disable, kept until a code from the method confirms it.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public final class VerificationDisablePendingState {
	private @NotNull String methodId;
	private long requestedAt;
}
