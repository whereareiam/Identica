package me.whereareiam.identica.provider.credential.database;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.schema.Schema;
import me.whereareiam.dialectica.schema.change.DropTable;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountEntity;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountHistoryEntity;
import me.whereareiam.strata.Migration;
import me.whereareiam.strata.adapter.database.DatabaseContext;
import me.whereareiam.strata.adapter.database.dialectica.SchemaMigration;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.OptionalInt;

/**
 * The tables of the credential provider: the password of each provider subject and the history of
 * its changes.
 *
 * <p>A database that has the credential accounts table already has version 1, the layout the
 * provider created before migrations, from Identica 2.2.1 or a development build of 3.0.0. Version 2
 * drops {@code dialectica_schema_migrations}, where 2.x recorded the provider's own migrations. A
 * database those migrations have not finished on, with the tables of the cracked provider of 1.x or
 * with fewer than both recorded, is refused: Identica 2.2.1 has to start on it once first.
 */
public final class CredentialSchema implements DatabaseSchema {
	/** Name Strata tracks the credential tables under; it never changes. */
	public static final String STREAM = "identica/provider/credential";

	/** Where Identica 2.x recorded the migrations of the credential provider. */
	private static final String LEGACY_MIGRATIONS = "dialectica_schema_migrations";
	/** Scope of those migrations; 2.2.1 finished with version 2. */
	private static final String LEGACY_SCOPE = "credential-accounts";
	private static final int LEGACY_LATEST = 2;
	/** Tables of the cracked provider of Identica 1.x, which 2.x renamed. */
	private static final List<String> CRACKED_TABLES = List.of("identica_cracked_accounts", "identica_cracked_account_passwords");

	private static final List<Migration<DatabaseContext>> MIGRATIONS = List.of(
			// Changes nothing: version 1 is the layout the provider created before migrations existed.
			new Migration<>(1, "initial-layout", database -> {}),
			SchemaMigration.of(2, "legacy-migration-history", new DropTable(LEGACY_MIGRATIONS))
	);

	@Override
	public @NotNull String stream() {
		return STREAM;
	}

	@Override
	public @NotNull Schema schema() {
		return Schema.of(
				Dialectica.entity(CredentialAccountEntity.class).table(),
				Dialectica.entity(CredentialAccountHistoryEntity.class).table()
		);
	}

	@Override
	public @NotNull List<Migration<DatabaseContext>> migrations() {
		return MIGRATIONS;
	}

	@Override
	public @NotNull OptionalInt baseline(@NotNull DatabaseContext database, int latest) throws SQLException {
		for (String table : CRACKED_TABLES)
			if (database.tableExists(table)) throw olderThan221("it still has the table " + table + " of Identica 1.x");

		OptionalInt recorded = legacyVersion(database);
		if (recorded.isPresent() && recorded.getAsInt() < LEGACY_LATEST)
			throw olderThan221(LEGACY_SCOPE + " is at version " + recorded.getAsInt() + " of " + LEGACY_LATEST);

		return database.tableExists("identica_provider_credential_accounts") ? OptionalInt.of(1) : OptionalInt.empty();
	}

	/** Returns the last version 2.x recorded for its credential migrations, if it recorded any. */
	private static OptionalInt legacyVersion(DatabaseContext database) throws SQLException {
		if (!database.tableExists(LEGACY_MIGRATIONS)) return OptionalInt.empty();

		try (PreparedStatement statement = database.getConnection().prepareStatement(
				"SELECT MAX(version) FROM " + LEGACY_MIGRATIONS + " WHERE scope = ?")) {
			statement.setString(1, LEGACY_SCOPE);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) return OptionalInt.empty();

				int version = rows.getInt(1);
				return rows.wasNull() ? OptionalInt.empty() : OptionalInt.of(version);
			}
		}
	}

	private static IllegalStateException olderThan221(String reason) {
		return new IllegalStateException("The credential tables come from a version of Identica older than 2.2.1 ("
				+ reason + "). Start Identica 2.2.1 on this database once, then this version.");
	}
}
