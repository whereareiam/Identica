package me.whereareiam.identica.adapter.database.repository.account;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.UUID;

/**
 * The account queries beyond reading and writing one account by its key, which
 * {@code EntityAccess<AccountEntity>} does.
 */
@RegisterDialecticaMapper(AccountEntity.class)
public interface AccountRepository {
	@SqlQuery("SELECT * FROM identica_accounts WHERE LOWER(username) = LOWER(:username)")
	List<AccountEntity> findByUsername(@Bind("username") String username);

	@SqlQuery("SELECT COUNT(*) FROM identica_accounts")
	long count();

	@SqlUpdate("""
			UPDATE identica_accounts
			   SET last_seen_at = :lastSeenAt
			 WHERE unique_id = :uniqueId
			""")
	void updateLastSeen(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("lastSeenAt") long lastSeenAt
	);

	@SqlUpdate("""
			UPDATE identica_accounts
			   SET username = :username
			 WHERE unique_id = :uniqueId
			""")
	void updateUsername(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("username") String username
	);
}
