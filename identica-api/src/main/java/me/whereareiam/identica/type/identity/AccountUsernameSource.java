package me.whereareiam.identica.type.identity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes what decides an account's username.
 */
@RequiredArgsConstructor
public enum AccountUsernameSource {
	/**
	 * The account follows the name its provider gives it.
	 */
	PROVIDER("provider"),

	/**
	 * An administrator named the account; Identica never changes the name by itself.
	 */
	MANUAL("manual"),

	/**
	 * Identica renamed the account in a username conflict; the account takes its provider's name back once nobody
	 * holds it.
	 */
	SYSTEM("system");

	@Getter
	private final @NotNull String id;

	/**
	 * Resolves a persisted source identifier into an enum value.
	 *
	 * @param id persisted source identifier
	 * @return resolved source, or {@link #PROVIDER} when missing or unknown
	 */
	public static @NotNull AccountUsernameSource fromId(@Nullable String id) {
		if (id == null || id.isBlank()) return PROVIDER;

		String normalized = id.trim().toLowerCase();
		for (AccountUsernameSource source : values())
			if (source.id.equals(normalized)) return source;

		return PROVIDER;
	}
}
