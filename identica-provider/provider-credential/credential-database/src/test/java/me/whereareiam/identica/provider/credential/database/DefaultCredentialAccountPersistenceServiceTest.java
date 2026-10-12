package me.whereareiam.identica.provider.credential.database;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountEntity;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountHistoryEntity;
import me.whereareiam.identica.provider.credential.database.repository.CredentialAccountRepository;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.model.CredentialAccountHistory;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Credential persistence")
class DefaultCredentialAccountPersistenceServiceTest {
	@DisplayName("Stores a credential, changes its password, records the change and removes it")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesCredentials(TestDatabase database) {
		database.reset();
		DefaultDatabaseService databaseService = database.open();
		try {
			databaseService.getSchemaInstaller().install(new CredentialSchema());
			Jdbi jdbi = databaseService.getJdbi();
			DefaultCredentialAccountPersistenceService service = new DefaultCredentialAccountPersistenceService(
					EntityAccess.of(jdbi, Dialectica.entity(CredentialAccountEntity.class)),
					EntityAccess.of(jdbi, Dialectica.entity(CredentialAccountHistoryEntity.class)),
					jdbi.onDemand(CredentialAccountRepository.class)
			);

			service.create(new CredentialAccount("credential", "alice", "hash", "ARGON2", 10L, 10L));
			assertEquals("hash", service.create(new CredentialAccount("credential", "alice", "other", "BCRYPT", 20L, 20L)).getPasswordHash());

			service.updatePassword("credential", "alice", "changed", "BCRYPT", 30L);
			service.recordPasswordChange(new CredentialAccountHistory("credential", "alice", "BCRYPT", PasswordChangeReason.CHANGE, 30L));

			CredentialAccount stored = service.findBySubject("credential", "alice").orElseThrow();
			assertEquals("changed", stored.getPasswordHash());
			assertEquals("BCRYPT", stored.getHashingMethod());
			assertEquals(10L, stored.getCreatedAt());
			assertEquals(30L, stored.getUpdatedAt());
			long changes = jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM identica_provider_credential_accounts_history")
					.mapTo(Long.class)
					.one());
			assertEquals(1L, changes);

			service.delete("credential", "alice");

			assertTrue(service.findBySubject("credential", "alice").isEmpty());
		} finally {
			TestDatabase.close(databaseService);
		}
	}
}
