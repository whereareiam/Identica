package me.whereareiam.identica.integration.statistics.database;

import me.whereareiam.identica.adapter.database.testing.schema.DatabaseSchemaContract;
import me.whereareiam.identica.adapter.database.testing.schema.SchemaSnapshot;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.database.schema.DatabaseSchema;
import me.whereareiam.identica.type.DatabaseType;

import java.util.List;
import java.util.Map;
import java.util.Set;

class StatisticsSchemaTest extends DatabaseSchemaContract {
	@Override
	protected DatabaseSchema schema() {
		return new StatisticsSchema();
	}

	@Override
	protected List<String> developmentBuild() {
		return List.of("development-build/account", "development-build/statistics");
	}

	@Override
	protected List<String> tables() {
		return List.of(StatisticsLeaseEntity.TABLE);
	}

	@Override
	protected List<String> rows() {
		return List.of("INSERT INTO identica_statistics_lease (id, holder, expires_at) VALUES (1, '00000000-0000-0000-0000-000000000001', 1)");
	}

	/**
	 * Development builds created the table with the same SQL on every database, {@code id INT} and
	 * {@code expires_at BIGINT}. SQLite reports those type names as written and keeps the key in an
	 * index of its own; Dialectica writes SQLite's {@code INTEGER}, which makes {@code id} the row
	 * id. Both columns have integer affinity either way, and the lease stores small integers.
	 */
	@Override
	protected SchemaSnapshot created(TestDatabase database, SchemaSnapshot developmentBuild) {
		if (database.type() != DatabaseType.SQLITE) return developmentBuild;

		return new SchemaSnapshot(Map.of(StatisticsLeaseEntity.TABLE, new SchemaSnapshot.Table(
				List.of("id INTEGER(2000000000) key", "holder VARCHAR(36) not null", "expires_at INTEGER(2000000000) not null"),
				List.of("id"),
				Set.of(),
				Set.of()
		)));
	}
}
