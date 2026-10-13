package me.whereareiam.identica.adapter.database.repository.account;

import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * The account queries beyond reading and writing one account by its key, which
 * {@code EntityAccess<AccountEntity>} does.
 */
public interface AccountRepository {
	@SqlQuery("SELECT COUNT(*) FROM identica_accounts")
	long count();
}
