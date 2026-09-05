package me.whereareiam.identica.provider.credential.database;

import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.config.persistence.H2Persistence;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Credential Dialectica Schema Contributor")
class CredentialSchemaContributorTest {
	private DefaultDatabaseService databaseService;
	private Jdbi jdbi;

    @BeforeEach
	void setUp() throws Exception {
        Path dataPath = Files.createTempDirectory("credential-schema-contributor-");

		H2Persistence persistence = new H2Persistence();
		persistence.setFile("mem:credential_schema;DB_CLOSE_DELAY=-1");
		persistence.setOptions("MODE=PostgreSQL");

		databaseService = new DefaultDatabaseService(
				persistence,
				Mockito.mock(EventManager.class),
                dataPath
		);
		jdbi = databaseService.getJdbi();

		jdbi.useHandle(handle -> {
			handle.execute("DROP TABLE IF EXISTS dialectica_schema_migrations");
			handle.execute("DROP TABLE IF EXISTS identica_provider_credential_accounts_history");
			handle.execute("DROP TABLE IF EXISTS identica_provider_credential_accounts");
		});
	}

	@AfterEach
	void tearDown() {
		if (databaseService != null)
			databaseService.onShutdown(null);
	}

	@Test
	void freshInstallCreatesCanonicalTablesWithoutMigrationHistory() throws Exception {
		databaseService.apply(new CredentialSchemaContributor());
		databaseService.apply(new CredentialSchemaContributor());

		assertTrue(tableExists("identica_provider_credential_accounts"));
		assertTrue(tableExists("identica_provider_credential_accounts_history"));
		assertFalse(tableExists("dialectica_schema_migrations"));
		assertFalse(tableExists("strata_history"));
	}

	private boolean tableExists(String table) throws Exception {
		return jdbi.withHandle(handle -> {
			try (var tables = handle.getConnection().getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
				while (tables.next())
					if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
			}
			return false;
		});
	}
}
