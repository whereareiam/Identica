package me.whereareiam.identica.feature.verification.database;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.Schema;
import me.whereareiam.dialectica.schema.change.AddColumn;
import me.whereareiam.dialectica.schema.type.ColumnType;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import me.whereareiam.identica.feature.verification.database.entity.VerificationRecoveryCodeEntity;
import me.whereareiam.identica.feature.verification.database.entity.VerificationSelectionEntity;
import me.whereareiam.strata.Migration;
import me.whereareiam.strata.adapter.database.DatabaseContext;
import me.whereareiam.strata.adapter.database.dialectica.SchemaMigration;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The tables of the verification feature: enrollments, recovery codes and the method selected per
 * provider, each belonging to an account. A database that has the enrollments table already has
 * version 1, the layout the feature created before migrations. Version 2 adds the counter of the
 * code each enrollment accepted last, so a code is accepted once.
 */
public final class VerificationSchema implements DatabaseSchema {
	/** Name Strata tracks the verification tables under; it never changes. */
	public static final String STREAM = "identica/feature/verification";

	private static final List<Migration<DatabaseContext>> MIGRATIONS = List.of(
			// Changes nothing: version 1 is the layout the feature created before migrations existed.
			new Migration<>(1, "initial-layout", database -> {}),
			// Existing enrollments start at counter 0, so their next code is accepted once like any other.
			SchemaMigration.of(2, "accepted-counter", new AddColumn(
					VerificationEnrollmentEntity.TABLE,
					ColumnDefinition.builder(VerificationEnrollmentEntity.ACCEPTED_COUNTER, ColumnType.bigint()).notNull().defaultValue(0L).build()
			))
	);

	@Override
	public @NotNull String stream() {
		return STREAM;
	}

	@Override
	public @NotNull Schema schema() {
		return Schema.of(
				Dialectica.entity(VerificationEnrollmentEntity.class).table(),
				Dialectica.entity(VerificationRecoveryCodeEntity.class).table(),
				Dialectica.entity(VerificationSelectionEntity.class).table()
		);
	}

	@Override
	public @NotNull List<Migration<DatabaseContext>> migrations() {
		return MIGRATIONS;
	}

	@Override
	public @NotNull OptionalInt baseline(@NotNull DatabaseContext database, int latest) throws SQLException {
		return database.tableExists(VerificationEnrollmentEntity.TABLE) ? OptionalInt.of(1) : OptionalInt.empty();
	}

	@Override
	public @NotNull Set<String> requires() {
		return Set.of(ACCOUNT_STREAM);
	}
}
