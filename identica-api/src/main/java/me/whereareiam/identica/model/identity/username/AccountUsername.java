package me.whereareiam.identica.model.identity.username;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The current username of an account and what decides it.
 *
 * <p>Usernames are unique without regard to case. Accounts from before that rule, and accounts of a provider that
 * vouches for its usernames, may still share one with an older account; each of them logs in through its own
 * provider link.
 */
@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true)
public class AccountUsername {
	/**
	 * The account.
	 */
	private final @NotNull UUID uniqueId;
	/**
	 * The username, in the case the account uses.
	 */
	private final @NotNull String username;
	/**
	 * What decides the username: the account's provider, an administrator, or Identica itself.
	 */
	private final @NotNull AccountUsernameSource source;
	/**
	 * The provider that renamed the account last, or {@code null} when no provider has.
	 */
	private final @Nullable String providerId;
	/**
	 * When the username or its source changed last, in epoch milliseconds.
	 */
	private final long changedAt;
}
