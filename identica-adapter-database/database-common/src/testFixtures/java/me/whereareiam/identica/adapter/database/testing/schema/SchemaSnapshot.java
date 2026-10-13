package me.whereareiam.identica.adapter.database.testing.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tables as the database's own metadata describes them: each column with its type, size,
 * nullability and default, the primary key, the indexes by their columns, and the foreign keys with their
 * delete rule. Two databases with equal snapshots have the same schema, however it was written.
 * <p>
 * Primary key columns record no nullability, since SQLite reports them as nullable when the DDL
 * does not say otherwise, which changes nothing about the key.
 *
 * @param tables a description of each table, by lowercase name
 */
public record SchemaSnapshot(Map<String, Table> tables) {
	/**
	 * Describes the given tables.
	 */
	public static SchemaSnapshot of(Connection connection, Collection<String> tables) throws SQLException {
		Map<String, Table> described = new TreeMap<>();
		for (String table : tables)
			described.put(table, describe(connection, table));

		return new SchemaSnapshot(described);
	}

	/**
	 * Describes every table in the connection's schema.
	 */
	public static SchemaSnapshot everyTable(Connection connection) throws SQLException {
		return of(connection, tableNames(connection));
	}

	/**
	 * Returns the lowercase names of every table in the connection's schema.
	 */
	public static Set<String> tableNames(Connection connection) throws SQLException {
		Set<String> names = new TreeSet<>();
		try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
			while (tables.next()) names.add(lower(tables.getString("TABLE_NAME")));
		}

		return names;
	}

	/**
	 * Returns this snapshot with a plain index over the given columns of a table, as if it had been
	 * created.
	 */
	public SchemaSnapshot withIndex(String table, String... columns) {
		Map<String, Table> changed = new TreeMap<>(tables);
		Table described = changed.get(table);
		Set<String> indexes = new TreeSet<>(described.indexes());
		indexes.add(String.join(", ", columns));
		changed.put(table, new Table(described.columns(), described.primaryKey(), indexes, described.foreignKeys()));

		return new SchemaSnapshot(changed);
	}

	/**
	 * Returns this snapshot with a table added or replaced, as if it had been created.
	 */
	public SchemaSnapshot withTable(String name, Table table) {
		Map<String, Table> changed = new TreeMap<>(tables);
		changed.put(name, table);

		return new SchemaSnapshot(changed);
	}

	/**
	 * Returns this snapshot without a table, as if it had been dropped.
	 */
	public SchemaSnapshot withoutTable(String name) {
		Map<String, Table> changed = new TreeMap<>(tables);
		changed.remove(name);

		return new SchemaSnapshot(changed);
	}

	/**
	 * Returns this snapshot without a column of a table, as if it had been dropped.
	 */
	public SchemaSnapshot withoutColumn(String table, String column) {
		Map<String, Table> changed = new TreeMap<>(tables);
		Table described = changed.get(table);
		List<String> columns = described.columns().stream()
				.filter(name -> !name.startsWith(column + " "))
				.toList();
		changed.put(table, new Table(columns, described.primaryKey(), described.indexes(), described.foreignKeys()));

		return new SchemaSnapshot(changed);
	}

	/**
	 * Returns the description of a column of a table, as {@code name TYPE(size) nullability}.
	 */
	public String column(String table, String column) {
		return tables.get(table).columns().stream()
				.filter(described -> described.startsWith(column + " "))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(table + " has no column " + column));
	}

	/**
	 * Returns this snapshot with a column appended to a table, as if it had been added.
	 *
	 * @param column the column as {@code name TYPE(size) nullability}, followed by
	 *               {@code default value} when it has a default
	 */
	public SchemaSnapshot withColumn(String table, String column) {
		Map<String, Table> changed = new TreeMap<>(tables);
		Table described = changed.get(table);
		List<String> columns = new ArrayList<>(described.columns());
		columns.add(column);
		changed.put(table, new Table(columns, described.primaryKey(), described.indexes(), described.foreignKeys()));

		return new SchemaSnapshot(changed);
	}

	private static Table describe(Connection connection, String table) throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String stored = metaData.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
		String catalog = connection.getCatalog();
		String schema = connection.getSchema();

		Map<Integer, String> keyOrder = new TreeMap<>();
		try (ResultSet keys = metaData.getPrimaryKeys(catalog, schema, stored)) {
			while (keys.next()) keyOrder.put(keys.getInt("KEY_SEQ"), lower(keys.getString("COLUMN_NAME")));
		}
		List<String> primaryKey = List.copyOf(keyOrder.values());

		List<String> columns = new ArrayList<>();
		try (ResultSet rows = metaData.getColumns(catalog, schema, stored, "%")) {
			while (rows.next()) {
				String column = lower(rows.getString("COLUMN_NAME"));
				String nullability = primaryKey.contains(column) ? "key" : rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "null" : "not null";
				columns.add(column + " " + rows.getString("TYPE_NAME").toUpperCase(Locale.ROOT) + "(" + rows.getInt("COLUMN_SIZE") + ") " + nullability
						+ defaultOf(rows.getString("COLUMN_DEF")));
			}
		}
		if (columns.isEmpty()) throw new IllegalStateException("Table " + table + " does not exist");

		Map<String, Map<Integer, String>> indexColumns = new HashMap<>();
		Map<String, Boolean> unique = new HashMap<>();
		try (ResultSet rows = metaData.getIndexInfo(catalog, schema, stored, false, false)) {
			while (rows.next()) {
				String name = rows.getString("INDEX_NAME");
				String column = rows.getString("COLUMN_NAME");
				if (name == null || column == null) continue;

				indexColumns.computeIfAbsent(name, ignored -> new TreeMap<>()).put((int) rows.getShort("ORDINAL_POSITION"), lower(column));
				unique.put(name, !rows.getBoolean("NON_UNIQUE"));
			}
		}
		Set<String> indexes = new TreeSet<>();
		indexColumns.forEach((name, ordered) -> indexes.add((unique.get(name) ? "unique " : "") + String.join(", ", ordered.values())));

		Set<String> foreignKeys = new TreeSet<>();
		try (ResultSet rows = metaData.getImportedKeys(catalog, schema, stored)) {
			while (rows.next())
				foreignKeys.add(lower(rows.getString("FKCOLUMN_NAME")) + " -> " + lower(rows.getString("PKTABLE_NAME")) + "."
						+ lower(rows.getString("PKCOLUMN_NAME")) + " " + deleteRule(rows.getShort("DELETE_RULE")));
		}

		return new Table(columns, primaryKey, indexes, foreignKeys);
	}

	/**
	 * Describes a column's default as the database reports it, or nothing for a column without one.
	 * MariaDB reports {@code NULL} for a nullable column without a default, which is no default.
	 */
	private static String defaultOf(String reported) {
		if (reported == null || reported.equalsIgnoreCase("NULL")) return "";

		return " default " + reported;
	}

	private static String deleteRule(short rule) {
		return switch (rule) {
			case DatabaseMetaData.importedKeyCascade -> "cascade";
			case DatabaseMetaData.importedKeySetNull -> "set null";
			case DatabaseMetaData.importedKeyRestrict, DatabaseMetaData.importedKeyNoAction -> "refuse";
			default -> "rule " + rule;
		};
	}

	private static String lower(String name) {
		return name == null ? null : name.toLowerCase(Locale.ROOT);
	}

	/**
	 * One table of a snapshot.
	 *
	 * @param columns     each column as {@code name TYPE(size) nullability}, followed by
	 *                    {@code default value} when it has a default, in table order
	 * @param primaryKey  the primary key columns, in key order
	 * @param indexes     each index as its columns, prefixed with {@code unique} when it is
	 * @param foreignKeys each foreign key column as {@code column -> table.column rule}
	 */
	public record Table(List<String> columns, List<String> primaryKey, Set<String> indexes, Set<String> foreignKeys) {
	}
}
