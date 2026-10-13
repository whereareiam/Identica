package me.whereareiam.identica.persistence;

import me.whereareiam.identica.model.identity.Account;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for persisting and retrieving accounts.
 */
@SuppressWarnings("unused")
public interface AccountPersistenceService {
	/**
	 * Find an account by its unique id.
	 *
	 * @param uniqueId the account id
	 * @return account or empty if not found
	 */
	@NotNull Optional<Account> findByUniqueId(@NotNull UUID uniqueId);

	/**
	 * Find accounts by username, without regard to case. Usernames are unique without regard to case, but
	 * accounts from before that rule, and accounts of a provider that vouches for its usernames, may share one,
	 * so every one of them is returned.
	 *
	 * @param username account username
	 * @return matching accounts
	 */
	@NotNull List<Account> findByUsername(@NotNull String username);

	/**
	 * Counts stored account records.
	 *
	 * @return total persisted accounts
	 */
	long count();

	/**
	 * Create a new account record, with its username following its provider. The username is unique without regard
	 * to case; when an older account holds it already, the new account shares it with that account.
	 *
	 * @param account account to create
	 * @return created account
	 * @see me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService
	 */
	@NotNull Account create(@NotNull Account account);

	/**
	 * Update the last-seen timestamp for an account.
	 *
	 * @param uniqueId account id
	 * @param lastSeenAt epoch millis
	 */
	void updateLastSeen(@NotNull UUID uniqueId, long lastSeenAt);

	/**
	 * Delete an account record.
	 *
	 * @param uniqueId account id
	 */
	void delete(@NotNull UUID uniqueId);
}
