package me.whereareiam.identica.adapter.database.connection;

import com.zaxxer.hikari.HikariDataSource;
import me.whereareiam.identica.model.config.persistence.SqlitePersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Data Source Factory")
class DataSourceFactoryTest {
	@DisplayName("Turns foreign keys on for every SQLite connection, so deletes cascade as on the other databases")
	@Test
	void sqliteConnectionsEnforceForeignKeys(@TempDir Path dataPath) throws Exception {
		SqlitePersistence persistence = new SqlitePersistence();
		persistence.setFile("identica.db");

		try (HikariDataSource dataSource = (HikariDataSource) DataSourceFactory.create(persistence, dataPath);
			 Connection connection = dataSource.getConnection();
			 ResultSet pragma = connection.createStatement().executeQuery("PRAGMA foreign_keys")) {
			pragma.next();
			assertEquals(1, pragma.getInt(1));
		}
	}
}
