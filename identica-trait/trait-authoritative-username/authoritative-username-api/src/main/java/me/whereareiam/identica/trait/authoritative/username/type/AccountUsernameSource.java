package me.whereareiam.identica.trait.authoritative.username.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes which source currently controls username synchronization for an account.
 */
@RequiredArgsConstructor
public enum AccountUsernameSource {
	PROVIDER("provider"),
	MANUAL("manual"),
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
