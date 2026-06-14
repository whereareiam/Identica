package me.whereareiam.identica.model.routing.attempt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Normalized failure payload for one routing attempt.
 */
@Getter
@RequiredArgsConstructor
public class RoutingAttemptFailure {
	private final @NotNull RoutingAttemptFailureReason reason;
	private final @Nullable String detail;
}
