package me.whereareiam.identica.adapter.database.repository.username;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.UUID;

/**
 * The username history queries beyond recording one entry, which
 * {@code EntityAccess<UsernameHistoryEntity>} does.
 */
public interface UsernameHistoryRepository {
	@SqlUpdate("""
			DELETE FROM identica_account_username_history
			 WHERE unique_id = :uniqueId
			""")
	void deleteAll(@Bind("uniqueId") UUID uniqueId);
}
