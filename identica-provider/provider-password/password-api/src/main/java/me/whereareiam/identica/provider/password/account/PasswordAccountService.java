package me.whereareiam.identica.provider.password.account;

import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Service for managing password provider accounts.
 */
public interface PasswordAccountService {
	/**
	 * Find a password account by provider subject.
	 *
	 * @param providerSubject provider subject to search by
	 * @return account when found, otherwise empty
	 */
	@NotNull Optional<PasswordAccount> find(@Nullable String providerSubject);

	/**
	 * Register a new account using a pre-hashed password and hashing method.
	 *
	 * @param providerSubject provider subject to register
	 * @param passwordHash hashed password
	 * @param hashingMethod hashing method id
	 * @param reason password change reason
	 * @return newly created account or empty when the account already exists
	 */
	@NotNull Optional<PasswordAccount> register(
			@NotNull String providerSubject,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			@NotNull PasswordChangeReason reason
	);

	/**
	 * Update the account password hash and hashing method.
	 *
	 * @param account account to update
	 * @param passwordHash hashed password
	 * @param hashingMethod hashing method id
	 * @param reason password change reason
	 * @return {@code true} when the password was updated
	 */
	boolean updatePassword(
			@NotNull PasswordAccount account,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			@NotNull PasswordChangeReason reason
	);

	/**
	 * Delete an account by provider subject.
	 *
	 * @param providerSubject provider subject to delete
	 */
	void delete(@NotNull String providerSubject);
}
