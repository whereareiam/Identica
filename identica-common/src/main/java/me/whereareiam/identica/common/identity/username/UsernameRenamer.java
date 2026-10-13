package me.whereareiam.identica.common.identity.username;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.identity.username.UsernameRename;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.type.identity.username.UsernameRenameTaken;
import me.whereareiam.identica.type.identity.username.UsernameRenameTooLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Makes renamed usernames: the pattern filled in, kept to what Minecraft allows, and free of other accounts.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameRenamer {
	/** The longest username Minecraft allows. */
	static final int MAX_LENGTH = 16;

	private static final Pattern DISALLOWED = Pattern.compile("[^A-Za-z0-9_]");
	private static final int FIRST_NUMBER = 2;
	private static final int LAST_NUMBER = 9;

	private final AccountUsernamePersistenceService usernames;

	/**
	 * Renames a username for an account.
	 *
	 * @param username   the username being renamed
	 * @param providerId id of the renamed account's provider, or {@code null} when it has none
	 * @param settings   how to rename
	 * @param account    the account that gets the name, which may hold it already, or {@code null} for a new one
	 * @param reserved   names given to another account in the same conflict, which are not stored yet
	 * @return the renamed username, or {@code null} when the settings refuse every name that could be made
	 */
	public @Nullable String rename(
			@NotNull String username,
			@Nullable String providerId,
			@NotNull UsernameRename settings,
			@Nullable UUID account,
			@NotNull Set<String> reserved
	) {
		for (String candidate : candidates(username, providerId, settings)) {
			if (reserved.stream().anyMatch(candidate::equalsIgnoreCase)) continue;

			boolean free = usernames.findHolder(candidate)
					.map(holder -> Objects.equals(holder.getUniqueId(), account))
					.orElse(true);
			if (free) return candidate;
		}

		return null;
	}

	private @NotNull List<String> candidates(
			@NotNull String username,
			@Nullable String providerId,
			@NotNull UsernameRename settings
	) {
		List<String> candidates = new ArrayList<>();
		String plain = fit(username, providerId, "", settings);
		if (plain == null) return candidates;

		candidates.add(plain);
		if (settings.getTaken() == UsernameRenameTaken.REFUSE) return candidates;

		for (int number = FIRST_NUMBER; number <= LAST_NUMBER; number++) {
			String numbered = fit(username, providerId, String.valueOf(number), settings);
			if (numbered != null) candidates.add(numbered);
		}

		return candidates;
	}

	/**
	 * Fills the pattern in and adds the suffix, shortening the username part until the result fits when the settings
	 * allow it.
	 */
	private @Nullable String fit(
			@NotNull String username,
			@Nullable String providerId,
			@NotNull String suffix,
			@NotNull UsernameRename settings
	) {
		String part = username;
		String rendered = render(settings.getPattern(), part, providerId) + suffix;
		if (rendered.length() <= MAX_LENGTH) return valid(rendered);
		if (settings.getTooLong() == UsernameRenameTooLong.REFUSE) return null;

		while (rendered.length() > MAX_LENGTH && !part.isEmpty()) {
			part = part.substring(0, part.length() - 1);
			rendered = render(settings.getPattern(), part, providerId) + suffix;
		}

		return rendered.length() <= MAX_LENGTH ? valid(rendered) : null;
	}

	private static @Nullable String valid(@NotNull String rendered) {
		return rendered.isEmpty() ? null : rendered;
	}

	private static @NotNull String render(@NotNull String pattern, @NotNull String username, @Nullable String providerId) {
		String rendered = pattern
				.replace("{username}", username)
				.replace("{provider}", providerId == null ? "" : providerId);

		return DISALLOWED.matcher(rendered).replaceAll("");
	}
}
