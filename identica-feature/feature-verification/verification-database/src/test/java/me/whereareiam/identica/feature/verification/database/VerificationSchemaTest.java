package me.whereareiam.identica.feature.verification.database;

import me.whereareiam.identica.adapter.database.testing.schema.DatabaseSchemaContract;
import me.whereareiam.identica.adapter.database.testing.schema.EarlierInstallation;
import me.whereareiam.identica.adapter.database.testing.schema.SchemaSnapshot;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationSchemaTest extends DatabaseSchemaContract {
	private static final String ACCOUNT = "'00000000-0000-0000-0000-000000000001'";

	@Override
	protected DatabaseSchema schema() {
		return new VerificationSchema();
	}

	@Override
	protected List<String> developmentBuild() {
		return List.of("development-build/account", "development-build/verification");
	}

	@Override
	protected List<String> tables() {
		return List.of(
				"identica_verification_enrollments",
				"identica_verification_recovery_codes",
				"identica_verification_selections"
		);
	}

	@Override
	protected List<String> rows() {
		return List.of(
				"INSERT INTO identica_accounts (unique_id, username, created_at, last_seen_at) VALUES (" + ACCOUNT + ", 'Alice', 1, 2)",
				"INSERT INTO identica_verification_enrollments (unique_id, method_id, enrollment_id, credential, label, created_at, enabled_at) VALUES (" + ACCOUNT + ", 'totp', 'phone', 'secret', NULL, 1, 2)",
				"INSERT INTO identica_verification_recovery_codes (unique_id, method_id, code_hash, created_at, used_at) VALUES (" + ACCOUNT + ", 'totp', 'hash', 1, NULL)",
				"INSERT INTO identica_verification_selections (unique_id, provider_id, method_id, selected_at) VALUES (" + ACCOUNT + ", 'credential', 'totp', 1)"
		);
	}

	/**
	 * Version 2 adds {@code accepted_counter} to the enrollments, a {@code BIGINT} that refuses
	 * {@code NULL} and defaults to {@code 0}, as each database's metadata describes it; MariaDB
	 * reports as MySQL does.
	 */
	@Override
	protected SchemaSnapshot upgraded(TestDatabase database, SchemaSnapshot developmentBuild) {
		String type = switch (database.type()) {
			case H2 -> "BIGINT(64)";
			case SQLITE -> "INTEGER(2000000000)";
			case POSTGRES -> "INT8(19)";
			case MYSQL -> "BIGINT(19)";
		};
		return developmentBuild.withColumn(VerificationEnrollmentEntity.TABLE, VerificationEnrollmentEntity.ACCEPTED_COUNTER + " " + type + " not null default 0");
	}

	/**
	 * Identica 2.2.1 created the verification tables in its core, as the feature later did.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void bringsThe221TablesToTheTablesOfANewInstallation(TestDatabase database) {
		SchemaSnapshot expected = newInstallation(database);
		database.reset();
		EarlierInstallation.create(database, "identica-2.2.1/account");
		EarlierInstallation.create(database, "identica-2.2.1/verification");

		install(database);

		assertEquals(expected, database.connect(SchemaSnapshot::everyTable));
		assertEquals(List.of("1 baseline", "2 accepted-counter"), history(database));
	}
}
