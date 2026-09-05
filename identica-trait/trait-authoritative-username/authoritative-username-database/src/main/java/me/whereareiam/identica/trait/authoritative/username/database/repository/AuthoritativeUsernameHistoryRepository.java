package me.whereareiam.identica.trait.authoritative.username.database.repository;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.UUID;

public interface AuthoritativeUsernameHistoryRepository {
	@SqlUpdate("""
			INSERT INTO identica_account_username_history (
				unique_id, provider_id, old_username, new_username, source, changed_at
			) VALUES (
				:uniqueId, :providerId, :oldUsername, :newUsername, :source, :changedAt
			)
			""")
	void insert(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("providerId") String providerId,
			@Bind("oldUsername") String oldUsername,
			@Bind("newUsername") String newUsername,
			@Bind("source") String source,
			@Bind("changedAt") long changedAt
	);

	@SqlUpdate("""
			DELETE FROM identica_account_username_history
			 WHERE unique_id = :uniqueId
			""")
	void deleteAll(@Bind("uniqueId") UUID uniqueId);
}
