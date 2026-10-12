package me.whereareiam.identica.adapter.database.testing.schema;

import me.whereareiam.identica.type.DatabaseType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creates the tables, and the rows when it had any, that an earlier Identica left in a database
 * before its tables were installed through migrations. A layout is a test resource per owner and
 * database type, {@code schema/<layout>/<owner>/<type>.sql}, generated from that version's
 * hand-written statements and kept by the owner: the account layouts with this harness, since every
 * other schema requires the account tables, and every other owner's in the test fixtures of its own
 * module. The layouts are:
 * <ul>
 *     <li>{@code identica-2.2.1}: the release, with rows an installation of it could hold;</li>
 *     <li>{@code development-build-2026-06}: earlier development builds of 3.0.0, from June 8 on,
 *     where an owner's layout differs from the last one; each file's header names the builds;</li>
 *     <li>{@code development-build}: the last development builds before migrations.</li>
 * </ul>
 * Each block after a {@code -- statement} line runs as one JDBC statement, as that version ran it,
 * so a driver that executes only the first of several statements behaves as it did then.
 */
public final class EarlierInstallation {
	private static final String SEPARATOR = "-- statement";

	private EarlierInstallation() {
	}

	/**
	 * Creates a layout in the given test database.
	 *
	 * @param database the database, after {@link TestDatabase#reset()}
	 * @param layout   the layout and owner, such as {@code development-build/account}
	 */
	public static void create(TestDatabase database, String layout) {
		database.connect(connection -> {
			create(connection, database.type(), layout);
			return null;
		});
	}

	/**
	 * Creates a layout on a connection, and commits it when the connection does not commit on its own.
	 *
	 * @param connection connection to the database
	 * @param type       type of the database, which selects the statements
	 * @param layout     the layout and owner, such as {@code identica-2.2.1/account}
	 * @throws SQLException when a statement fails
	 */
	public static void create(Connection connection, DatabaseType type, String layout) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : statements(layout, type.name().toLowerCase(Locale.ROOT)))
				statement.execute(sql);
		}
		if (!connection.getAutoCommit()) connection.commit();
	}

	private static List<String> statements(String layout, String type) {
		String resource = "schema/" + layout + "/" + type + ".sql";
		String script;
		try (InputStream input = EarlierInstallation.class.getClassLoader().getResourceAsStream(resource)) {
			if (input == null) throw new IllegalArgumentException("Missing test resource " + resource);
			script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		List<String> statements = new ArrayList<>();
		StringBuilder current = null;
		for (String line : script.split("\n")) {
			if (line.strip().equals(SEPARATOR)) {
				if (current != null) statements.add(current.toString().strip());
				current = new StringBuilder();
			} else if (current != null) {
				current.append(line).append('\n');
			}
		}
		if (current != null) statements.add(current.toString().strip());

		return statements;
	}
}
