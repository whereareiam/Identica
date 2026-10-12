package me.whereareiam.identica.adapter.database.repository.account;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

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
}
