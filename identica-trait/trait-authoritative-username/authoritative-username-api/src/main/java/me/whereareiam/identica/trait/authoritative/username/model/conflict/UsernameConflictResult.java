package me.whereareiam.identica.trait.authoritative.username.model.conflict;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of username conflict resolution, including the effective name and any denial.
 */
@Getter
@RequiredArgsConstructor
public class UsernameConflictResult {
	private final boolean denied;
	private final @Nullable String denialMessage;
	private final @NotNull String effectiveUsername;

	/**
	 * Allows the flow to continue using the resolved username.
	 *
	 * @param effectiveUsername name to apply to the connection
	 * @return allowed outcome
	 */
	public static @NotNull UsernameConflictResult allowed(@NotNull String effectiveUsername) {
		return new UsernameConflictResult(false, null, effectiveUsername);
	}

	/**
	 * Denies the flow while retaining the username selected during resolution.
	 *
	 * @param denialMessage optional denial message; null lets the caller choose a default
	 * @param effectiveUsername name selected before denial
	 * @return denied outcome
	 */
	public static @NotNull UsernameConflictResult denied(
			@Nullable String denialMessage,
			@NotNull String effectiveUsername
	) {
		return new UsernameConflictResult(true, denialMessage, effectiveUsername);
	}
}
