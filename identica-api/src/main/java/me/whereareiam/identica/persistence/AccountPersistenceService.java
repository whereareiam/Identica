package me.whereareiam.identica.persistence;

import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	 * accounts from before that rule may share one, so every one of them is returned.
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
	 * Create a new account record together with its username, both or neither. The username is unique without regard
	 * to case: when another account holds it already, no account is created and the result names the holder.
	 *
	 * @param account    account to create
	 * @param source     what decides the account's username
	 * @param providerId the provider the account registers through, or {@code null} when none
	 * @return whether the account was created, or the account that holds its username
	 * @throws RuntimeException when the storage fails
	 * @see me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService
	 */
	@NotNull UsernameWriteResult create(
			@NotNull Account account,
			@NotNull AccountUsernameSource source,
			@Nullable String providerId
	);

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
