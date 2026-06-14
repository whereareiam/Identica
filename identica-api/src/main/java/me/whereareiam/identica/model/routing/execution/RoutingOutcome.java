package me.whereareiam.identica.model.routing.execution;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Normalized platform outcome for one executed routing attempt.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class RoutingOutcome {
	private final boolean accepted;
	private final @NotNull String targetServer;
	private final @Nullable RoutingAttemptFailure failure;

	/**
	 * Creates a successful routing outcome.
	 *
	 * @param targetServer resolved target server name
	 * @return accepted routing outcome
	 */
	public static @NotNull RoutingOutcome accepted(@NotNull String targetServer) {
		return new RoutingOutcome(true, targetServer, null);
	}

	/**
	 * Creates a failed routing outcome.
	 *
	 * @param targetServer resolved target server name
	 * @param failureReason machine-readable failure reason
	 * @param failureDetail platform supplied plain-text reason when available
	 * @return failed routing outcome
	 */
	public static @NotNull RoutingOutcome failed(
			@NotNull String targetServer,
			@NotNull RoutingAttemptFailureReason failureReason,
			@Nullable String failureDetail
	) {
		return failed(targetServer, new RoutingAttemptFailure(failureReason, failureDetail));
	}

	/**
	 * Creates a failed routing outcome.
	 *
	 * @param targetServer resolved target server name
	 * @param failure normalized failure payload
	 * @return failed routing outcome
	 */
	public static @NotNull RoutingOutcome failed(
			@NotNull String targetServer,
			@NotNull RoutingAttemptFailure failure
	) {
		return new RoutingOutcome(false, targetServer, failure);
	}
}
