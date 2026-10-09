package me.whereareiam.identica.trait.authoritative.username.database.repository;

import me.whereareiam.identica.trait.authoritative.username.database.entity.AuthoritativeUsernameStateEntity;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;
import java.util.UUID;

@RegisterBeanMapper(AuthoritativeUsernameStateEntity.class)
public interface AuthoritativeUsernameStateRepository {
	@SqlQuery("""
			SELECT unique_id AS uniqueId,
			       source AS source,
			       updated_at AS updatedAt
			  FROM identica_account_username_state
			 WHERE unique_id = :uniqueId
			""")
	Optional<AuthoritativeUsernameStateEntity> findByUniqueId(@Bind("uniqueId") UUID uniqueId);

	@SqlUpdate("""
			INSERT INTO identica_account_username_state (unique_id, source, updated_at)
			VALUES (:uniqueId, :source, :updatedAt)
			""")
	void insert(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("source") String source,
			@Bind("updatedAt") long updatedAt
	);

	@SqlUpdate("""
			UPDATE identica_account_username_state
			   SET source = :source,
			       updated_at = :updatedAt
			 WHERE unique_id = :uniqueId
			""")
	void update(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("source") String source,
			@Bind("updatedAt") long updatedAt
	);

	@SqlUpdate("DELETE FROM identica_account_username_state WHERE unique_id = :uniqueId")
	void delete(@Bind("uniqueId") UUID uniqueId);
}
