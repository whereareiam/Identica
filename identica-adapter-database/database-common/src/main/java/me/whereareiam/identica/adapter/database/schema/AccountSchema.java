package me.whereareiam.identica.adapter.database.schema;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.Schema;
import me.whereareiam.dialectica.schema.TableDefinition;
import me.whereareiam.dialectica.schema.change.CreateTable;
import me.whereareiam.dialectica.schema.change.DropColumn;
import me.whereareiam.dialectica.schema.change.DropTable;
import me.whereareiam.dialectica.schema.type.ColumnType;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderProfileEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountReservationEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameHistoryEntity;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameMapper;
import me.whereareiam.identica.database.schema.AccountTable;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.strata.Migration;
import me.whereareiam.strata.adapter.database.DatabaseContext;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The core tables: accounts, their provider links and profiles, username reservations, and the
 * username state and history of an account.
 *
 * <p>A database without {@code identica_accounts} is a new installation. Every other one starts at 0
 * and runs version 1, which brings the username tables of Identica 2.2.1 and of the development
 * builds of 3.0.0 to the current layout. It checks the database before each step, so it changes only
 * what a database still lacks and can run again after it stopped halfway on a database whose DDL
 * commits on its own. Version 2 moves the username of each account, with its source, to its own
 * table, where its key keeps usernames unique without regard to case; it too can run again.
 */
public final class AccountSchema implements DatabaseSchema {
	/** Username history of Identica 2.2.1, without a foreign key to the account. */
	private static final String LEGACY_HISTORY = "identica_username_history";
	/** Column of Identica 2.2.1 that held the source of an account's username. */
	private static final String LEGACY_SOURCE = "username_source";
	/** Column of the accounts that held their username before version 2. */
	private static final String LEGACY_USERNAME = "username";
	/** Username states of versions 1 and earlier development builds, which version 2 merges into the usernames. */
	private static final String LEGACY_STATE = "identica_account_username_state";
	/** The username state table as version 1 creates it; version 2 drops it again. */
	private static final TableDefinition LEGACY_STATE_TABLE = TableDefinition.builder(LEGACY_STATE)
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("source", ColumnType.varchar(32)).notNull().build())
			.column(ColumnDefinition.builder("updated_at", ColumnType.bigint()).notNull().build())
			.primaryKey("unique_id")
			.build();
	/** Orders the accounts of one username: the oldest keeps the key, accounts of unknown age come last. */
	private static final Comparator<LegacyAccount> OLDEST_FIRST = Comparator
			.comparing(LegacyAccount::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(account -> account.uniqueId().toString());

	private static final List<Migration<DatabaseContext>> MIGRATIONS = List.of(
			new Migration<>(1, "username-layout", AccountSchema::moveUsernameLayout),
			new Migration<>(2, "username-table", AccountSchema::moveUsernamesToTheirTable)
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
				Dialectica.entity(UsernameEntity.class).table(),
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
				new CreateTable(LEGACY_STATE_TABLE),
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

	/**
	 * Moves the username of each account to {@code identica_account_usernames}, with the source and
	 * time of its username state; an account without a state follows its provider since it last
	 * logged in. Of the accounts that share a username without regard to case, the oldest holds its
	 * key, by creation time and then unique id; the others keep {@code NULL}, which the unique index
	 * accepts any number of times, and go on logging in through their own provider links. The keys
	 * are lowered in Java, because SQLite lowers only ASCII letters. The usernames are written anew
	 * while the old column exists, so a run that stopped halfway finishes on the next, before the
	 * column and the state table are dropped.
	 */
	private static void moveUsernamesToTheirTable(DatabaseContext database) throws SQLException {
		Connection connection = database.getConnection();
		Dialectica.on(connection).apply(new CreateTable(Dialectica.entity(UsernameEntity.class).table()));
		if (database.columnExists(AccountTable.NAME, LEGACY_USERNAME)) writeUsernames(database);

		Dialectica.on(connection).apply(
				new DropColumn(AccountTable.NAME, LEGACY_USERNAME),
				new DropTable(LEGACY_STATE)
		);
	}

	private static void writeUsernames(DatabaseContext database) throws SQLException {
		Connection connection = database.getConnection();
		Dialect dialect = Dialect.of(connection.getMetaData());
		Map<UUID, LegacyState> states = database.tableExists(LEGACY_STATE) ? legacyStates(connection, dialect) : Map.of();
		Map<String, List<LegacyAccount>> usernames = legacyAccounts(connection, dialect);
		long now = System.currentTimeMillis();

		database.update("DELETE FROM identica_account_usernames");
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO identica_account_usernames (unique_id, username, username_key, source, provider_id, changed_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""")) {
			for (Map.Entry<String, List<LegacyAccount>> username : usernames.entrySet()) {
				List<LegacyAccount> accounts = username.getValue();
				for (LegacyAccount account : accounts) {
					LegacyState state = states.get(account.uniqueId());
					dialect.bind(statement, 1, ColumnType.uuid(), account.uniqueId());
					dialect.bind(statement, 2, ColumnType.varchar(64), account.username());
					dialect.bind(statement, 3, ColumnType.varchar(64), account == accounts.getFirst() ? username.getKey() : null);
					dialect.bind(statement, 4, ColumnType.varchar(32), state != null ? state.source() : "provider");
					dialect.bind(statement, 5, ColumnType.varchar(64), null);
					dialect.bind(statement, 6, ColumnType.bigint(), state != null ? state.updatedAt() : account.lastChange(now));
					statement.addBatch();
				}
			}
			statement.executeBatch();
		}

		long shared = usernames.values().stream().filter(accounts -> accounts.size() > 1).count();
		if (shared > 0)
			Logger.info("%d usernames are shared by several accounts without regard to case; the oldest account of each holds it, the others keep logging in through their own providers", shared);
	}

	private static Map<UUID, LegacyState> legacyStates(Connection connection, Dialect dialect) throws SQLException {
		Map<UUID, LegacyState> states = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT unique_id, source, updated_at FROM identica_account_username_state");
			 ResultSet rows = statement.executeQuery()) {
			while (rows.next())
				states.put(
						(UUID) dialect.read(rows, 1, ColumnType.uuid()),
						new LegacyState(rows.getString(2), (Long) dialect.read(rows, 3, ColumnType.bigint()))
				);
		}

		return states;
	}

	private static Map<String, List<LegacyAccount>> legacyAccounts(Connection connection, Dialect dialect) throws SQLException {
		Map<String, List<LegacyAccount>> usernames = new TreeMap<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT unique_id, username, created_at, last_seen_at FROM identica_accounts");
			 ResultSet rows = statement.executeQuery()) {
			while (rows.next()) {
				LegacyAccount account = new LegacyAccount(
						(UUID) dialect.read(rows, 1, ColumnType.uuid()),
						rows.getString(2),
						(Long) dialect.read(rows, 3, ColumnType.bigint()),
						(Long) dialect.read(rows, 4, ColumnType.bigint())
				);
				usernames.computeIfAbsent(UsernameMapper.usernameKey(account.username()), key -> new ArrayList<>()).add(account);
			}
		}
		usernames.values().forEach(accounts -> accounts.sort(OLDEST_FIRST));

		return usernames;
	}

	private record LegacyAccount(UUID uniqueId, String username, Long createdAt, Long lastSeenAt) {
		/** Returns when the account's username last changed as far as is known: its last login, or its creation. */
		long lastChange(long now) {
			if (lastSeenAt != null) return lastSeenAt;

			return createdAt != null ? createdAt : now;
		}
	}

	private record LegacyState(String source, long updatedAt) {
	}
}
