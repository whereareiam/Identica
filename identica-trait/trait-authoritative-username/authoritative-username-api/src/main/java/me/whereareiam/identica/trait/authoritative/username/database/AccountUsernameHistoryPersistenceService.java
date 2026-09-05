package me.whereareiam.identica.trait.authoritative.username.database;

import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameHistoryEntry;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Records and removes persisted username-history entries for accounts.
 */
public interface AccountUsernameHistoryPersistenceService {
	/**
	 * Records a username-history entry.
	 *
	 * @param entry history entry to append
	 */
	void record(@NotNull AccountUsernameHistoryEntry entry);

	/**
	 * Deletes all username-history entries for an account.
	 *
	 * @param uniqueId account unique id
	 */
	void deleteAll(@NotNull UUID uniqueId);
}
