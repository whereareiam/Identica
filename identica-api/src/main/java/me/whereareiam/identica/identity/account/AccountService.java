package me.whereareiam.identica.identity.account;

import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.AccountOperationRequest;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for account lookup and lifecycle operations.
 */
@SuppressWarnings("unused")
public interface AccountService {
	/**
	 * Find an account by its unique id.
	 *
	 * @param uniqueId account id
	 * @return account or empty if not found
	 */
	@NotNull Optional<Account> find(@NotNull UUID uniqueId);

	/**
	 * Find accounts by username.
	 *
	 * @param username account username
	 * @return matching accounts
	 */
	@NotNull List<Account> find(@NotNull String username);

	/**
	 * Create a new account with its username. Usernames are unique without regard to case: when another account
	 * holds the username already, no account is created and the result names the holder.
	 *
	 * @param account    account to create
	 * @param source     what decides the account's username
	 * @param providerId the provider the account registers through, or {@code null} when none
	 * @return whether the account was created, or the account that holds its username
	 */
	@NotNull UsernameWriteResult create(
			@NotNull Account account,
			@NotNull AccountUsernameSource source,
			@Nullable String providerId
	);

	/**
	 * Clear account-owned data while preserving the account UUID.
	 *
	 * @param request clear request
	 */
	void clear(@NotNull AccountOperationRequest request);

	/**
	 * Delete the account and its UUID completely.
	 *
	 * @param request delete request
	 */
	void delete(@NotNull AccountOperationRequest request);
}
