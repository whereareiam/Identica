package me.whereareiam.identica.persistence.username;

import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and changes the current username of accounts. An account gets its username when it is created and loses it
 * with the account; renaming it also records the change in the account's username history.
 */
public interface AccountUsernamePersistenceService {
	/**
	 * Finds the current username of an account.
	 *
	 * @param uniqueId the account
	 * @return the username, or empty when the account does not exist
	 */
	@NotNull Optional<AccountUsername> find(@NotNull UUID uniqueId);

	/**
	 * Changes what decides an account's username, keeping the username itself.
	 *
	 * @param uniqueId the account
	 * @param source   what decides the username from now on
	 */
	void setSource(@NotNull UUID uniqueId, @NotNull AccountUsernameSource source);

	/**
	 * Renames an account and appends the change to its username history, both or neither. The account takes the
	 * change's new username, source and provider; another account that holds the new username without regard to case
	 * keeps it, and this account keeps its own.
	 *
	 * <pre>{@code
	 * boolean renamed = usernames.rename(AccountUsernameHistoryEntry.builder()
	 *         .uniqueId(account)
	 *         .providerId("example")
	 *         .oldUsername("Alice")
	 *         .newUsername("Alicia")
	 *         .source(AccountUsernameSource.PROVIDER)
	 *         .changedAt(System.currentTimeMillis())
	 *         .build());
	 * }</pre>
	 *
	 * @param change the change, which becomes the history entry
	 * @return whether the account was renamed
	 */
	boolean rename(@NotNull AccountUsernameHistoryEntry change);
}
