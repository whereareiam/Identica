package me.whereareiam.identica.trait.authoritative.username.database;

import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameState;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Persists and retrieves the current username-control state for accounts.
 */
public interface AccountUsernameStatePersistenceService {
	/**
	 * Finds the current username-control state for an account.
	 *
	 * @param uniqueId account unique id
	 * @return stored state, or empty when none exists
	 */
	@NotNull Optional<AccountUsernameState> find(@NotNull UUID uniqueId);

	/**
	 * Saves the current username-control state for an account.
	 *
	 * @param uniqueId account unique id
	 * @param source current username source
	 */
	void save(@NotNull UUID uniqueId, @NotNull AccountUsernameSource source);

	/**
	 * Deletes any stored username-control state for an account.
	 *
	 * @param uniqueId account unique id
	 */
	void delete(@NotNull UUID uniqueId);
}
