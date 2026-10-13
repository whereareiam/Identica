package me.whereareiam.identica.adapter.database.repository.username;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * The username queries beyond reading and writing the username of one account, which
 * {@code EntityAccess<UsernameEntity>} does.
 */
@RegisterDialecticaMapper(UsernameEntity.class)
public interface UsernameRepository {
	/**
	 * Finds the row that holds a username key, and every row without a key, among which {@link #findByUsername}
	 * picks those of the username: SQLite lowers only ASCII letters, so their usernames are compared in Java.
	 */
	@SqlQuery("SELECT * FROM identica_account_usernames WHERE username_key = :usernameKey OR username_key IS NULL")
	List<UsernameEntity> findByUsernameKeyOrWithoutKey(@Bind("usernameKey") String usernameKey);

	/**
	 * Finds the usernames of the accounts that use a username without regard to case: the one holding its key, and
	 * those that share it without one.
	 */
	default List<UsernameEntity> findByUsername(String username) {
		String usernameKey = UsernameMapper.usernameKey(username);

		return findByUsernameKeyOrWithoutKey(usernameKey).stream()
				.filter(row -> row.getUsernameKey() != null || UsernameMapper.usernameKey(row.getUsername()).equals(usernameKey))
				.toList();
	}

	/**
	 * Finds the usernames without a key, because an older account holds the key of each.
	 */
	@SqlQuery("SELECT * FROM identica_account_usernames WHERE username_key IS NULL")
	List<UsernameEntity> findWithoutUsernameKey();
}
