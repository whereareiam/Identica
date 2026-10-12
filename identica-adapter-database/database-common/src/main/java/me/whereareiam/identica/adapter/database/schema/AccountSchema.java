package me.whereareiam.identica.adapter.database.schema;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.schema.Schema;
import me.whereareiam.dialectica.schema.change.CreateTable;
import me.whereareiam.dialectica.schema.change.DropColumn;
import me.whereareiam.dialectica.schema.change.DropTable;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderProfileEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountReservationEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameHistoryEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameStateEntity;
import me.whereareiam.identica.database.schema.AccountTable;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.strata.Migration;
import me.whereareiam.strata.adapter.database.DatabaseContext;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.OptionalInt;

/**
 * The core tables: accounts, their provider links and profiles, username reservations, and the
 * username state and history of an account.
 *
 * <p>A database without {@code identica_accounts} is a new installation. Every other one starts at 0
 * and runs version 1, which brings the username tables of Identica 2.2.1 and of the development
 * builds of 3.0.0 to the current layout. It checks the database before each step, so it changes only
 * what a database still lacks and can run again after it stopped halfway on a database whose DDL
 * commits on its own.
 */
public final class AccountSchema implements DatabaseSchema {
	/** Username history of Identica 2.2.1, without a foreign key to the account. */
	private static final String LEGACY_HISTORY = "identica_username_history";
	/** Column of Identica 2.2.1 that held the source of an account's username. */
	private static final String LEGACY_SOURCE = "username_source";

	private static final List<Migration<DatabaseContext>> MIGRATIONS = List.of(
			new Migration<>(1, "username-layout", AccountSchema::moveUsernameLayout)
	);

	@Override
	public @NotNull String stream() {
		return ACCOUNT_STREAM;
	}

	@Override
	public @NotNull Schema schema() {
		return Schema.of(
				Dialectica.entity(AccountEntity.class).table(),
				Dialectica.entity(AccountProviderLinkEntity.class).table(),
				Dialectica.entity(AccountProviderProfileEntity.class).table(),
				Dialectica.entity(AccountReservationEntity.class).table(),
				Dialectica.entity(UsernameStateEntity.class).table(),
				Dialectica.entity(UsernameHistoryEntity.class).table()
		);
	}

	@Override
	public @NotNull List<Migration<DatabaseContext>> migrations() {
		return MIGRATIONS;
	}

	@Override
	public @NotNull OptionalInt baseline(@NotNull DatabaseContext database, int latest) throws SQLException {
		return database.tableExists(AccountTable.NAME) ? OptionalInt.of(0) : OptionalInt.empty();
	}

	/**
	 * Creates the username tables a database lacks, with their indexes: Identica 2.2.1 had neither,
	 * development builds left the history index out on SQLite and failed to create the history table
	 * on MySQL. Then moves what 2.2.1 kept elsewhere: a username source other than the provider's
	 * becomes a username state, and the history rows of existing accounts move to the new history,
	 * before the old table and column are dropped.
	 */
	private static void moveUsernameLayout(DatabaseContext database) throws SQLException {
		Dialectica.on(database.getConnection()).apply(
				new CreateTable(Dialectica.entity(UsernameStateEntity.class).table()),
				new CreateTable(Dialectica.entity(UsernameHistoryEntity.class).table())
		);

		if (database.columnExists(AccountTable.NAME, LEGACY_SOURCE))
			// An account without a state follows its provider, so only the other sources are kept.
			database.update("""
					INSERT INTO identica_account_username_state (unique_id, source, updated_at)
					SELECT account.unique_id, LOWER(TRIM(account.username_source)),
					       COALESCE(account.last_seen_at, account.created_at, ?)
					  FROM identica_accounts account
					 WHERE LOWER(TRIM(account.username_source)) IN ('manual', 'system')
					   AND NOT EXISTS (
							SELECT 1
							  FROM identica_account_username_state state
							 WHERE state.unique_id = account.unique_id
					   )
					""", System.currentTimeMillis());

		if (database.tableExists(LEGACY_HISTORY))
			// The old table had no foreign key, so rows of deleted accounts stay behind.
			database.update("""
					INSERT INTO identica_account_username_history (unique_id, provider_id, old_username, new_username, source, changed_at)
					SELECT legacy.unique_id, legacy.provider_id, legacy.old_username, legacy.new_username, legacy.source, legacy.changed_at
					  FROM identica_username_history legacy
					 WHERE EXISTS (
							SELECT 1
							  FROM identica_accounts account
							 WHERE account.unique_id = legacy.unique_id
					   )
					   AND NOT EXISTS (
							SELECT 1
							  FROM identica_account_username_history moved
							 WHERE moved.unique_id = legacy.unique_id
							   AND moved.changed_at = legacy.changed_at
							   AND moved.old_username = legacy.old_username
							   AND moved.new_username = legacy.new_username
							   AND moved.source = legacy.source
							   AND (moved.provider_id = legacy.provider_id OR (moved.provider_id IS NULL AND legacy.provider_id IS NULL))
					   )
					""");

		Dialectica.on(database.getConnection()).apply(
				new DropTable(LEGACY_HISTORY),
				new DropColumn(AccountTable.NAME, LEGACY_SOURCE)
		);
	}
}
