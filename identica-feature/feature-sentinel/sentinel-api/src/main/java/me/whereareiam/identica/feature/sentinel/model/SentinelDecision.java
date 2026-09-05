package me.whereareiam.identica.feature.sentinel.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of evaluating or recording a sentinel.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class SentinelDecision {
	private final boolean limited;
	private final boolean deny;
	private final long remainingSeconds;
	private final @Nullable String message;
	private final int remainingAttempts;
	private final @Nullable String warningMessage;
	private final @Nullable SentinelDefinition definition;

	/**
	 * Creates a denying limited decision with a message.
	 *
	 * @param definition originating definition
	 * @param remainingSeconds remaining lockout duration in seconds
	 * @param message denial message
	 * @return limited denying decision
	 */
	public static @NotNull SentinelDecision limited(
			@Nullable SentinelDefinition definition,
			long remainingSeconds,
			@Nullable String message
	) {
		return new SentinelDecision(true, true, remainingSeconds, message, 0, null, definition);
	}

	/**
	 * Creates a limited decision with explicit deny behavior.
	 *
	 * @param definition originating definition
	 * @param remainingSeconds remaining lockout duration in seconds
	 * @param message optional lockout message
	 * @param deny whether the decision should deny the action immediately
	 * @return limited decision
	 */
	public static @NotNull SentinelDecision limited(
			@Nullable SentinelDefinition definition,
			long remainingSeconds,
			@Nullable String message,
			boolean deny
	) {
		return new SentinelDecision(true, deny, remainingSeconds, message, 0, null, definition);
	}

	/**
	 * Creates an allowed decision without warning state.
	 *
	 * @param definition originating definition
	 * @return allowed decision
	 */
	public static @NotNull SentinelDecision allowed(@Nullable SentinelDefinition definition) {
		return new SentinelDecision(false, false, 0L, null, 0, null, definition);
	}

	/**
	 * Creates an allowed decision with warning metadata.
	 *
	 * @param definition originating definition
	 * @param remainingAttempts remaining attempts before a lockout
	 * @param warningMessage warning message to display
	 * @return allowed warning decision
	 */
	public static @NotNull SentinelDecision allowed(
			@Nullable SentinelDefinition definition,
			int remainingAttempts,
			@Nullable String warningMessage
	) {
		return new SentinelDecision(false, false, 0L, null, remainingAttempts, warningMessage, definition);
	}
}
