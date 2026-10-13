package me.whereareiam.identica.model.identity.username;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The username an arriving account gets, or why it is refused.
 */
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UsernameAssignment {
	private final boolean refused;

	/**
	 * Why the account is refused, or {@code null} when it is not.
	 */
	private final @Nullable String message;

	/**
	 * The username the account keeps, or {@code null} when it is refused.
	 */
	private final @Nullable String username;

	/**
	 * The username the player plays under this session, or {@code null} when refused: the account's username, or a
	 * temporary one while the holder of the account's username keeps playing.
	 */
	private final @Nullable String sessionUsername;

	/**
	 * What decides the username the account keeps, or {@code null} when it is refused.
	 */
	private final @Nullable AccountUsernameSource source;

	/**
	 * Creates an assignment of a username.
	 *
	 * @param username        username the account keeps
	 * @param sessionUsername username the player plays under this session
	 * @param source          what decides the username the account keeps
	 * @return assignment
	 */
	public static @NotNull UsernameAssignment of(
			@NotNull String username,
			@NotNull String sessionUsername,
			@NotNull AccountUsernameSource source
	) {
		return new UsernameAssignment(false, null, username, sessionUsername, source);
	}

	/**
	 * Creates a refusal.
	 *
	 * @param message why the account is refused
	 * @return refusal
	 */
	public static @NotNull UsernameAssignment refuse(@NotNull String message) {
		return new UsernameAssignment(true, message, null, null, null);
	}
}
