package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.identica.adapter.database.testing.DatabaseFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Schema Constraints")
class SchemaConstraintsIntegrationTest extends DatabaseIntegrationTestBase {
	@DisplayName("Deleting an account cascades to its username, provider links and profiles")
	@ParameterizedTest(name = "{0}")
	@MethodSource("fixtures")
	void cascadesDeletesFromAccounts(DatabaseFixture fixture) {
		fixture.reset();
		UUID uniqueId = UUID.randomUUID();

		fixture.useHandle(handle -> {
			handle.execute(
					"INSERT INTO identica_accounts (unique_id, created_at, last_seen_at) VALUES (?, ?, ?)",
					uniqueId,
					1L,
					2L
			);
			handle.execute(
					"INSERT INTO identica_provider_links (unique_id, provider_id, provider_subject, is_primary, linked_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)",
					uniqueId,
					"provider",
					"subject",
					true,
					3L,
					4L
			);
			handle.execute(
					"INSERT INTO identica_provider_profiles (provider_id, provider_subject, provider_username) VALUES (?, ?, ?)",
					"provider",
					"subject",
					"profileUser"
			);

			handle.execute(
					"INSERT INTO identica_account_usernames (unique_id, username, username_key, source, provider_id, changed_at) VALUES (?, ?, ?, ?, ?, ?)",
					uniqueId,
					"Player",
					"player",
					"provider",
					null,
					1L
			);

			handle.execute("DELETE FROM identica_accounts WHERE unique_id = ?", uniqueId);

			Long links = handle.createQuery("SELECT COUNT(*) FROM identica_provider_links")
					.mapTo(Long.class)
					.one();
			Long profiles = handle.createQuery("SELECT COUNT(*) FROM identica_provider_profiles")
					.mapTo(Long.class)
					.one();

			Long usernames = handle.createQuery("SELECT COUNT(*) FROM identica_account_usernames")
					.mapTo(Long.class)
					.one();

			assertEquals(0L, links);
			assertEquals(0L, profiles);
			assertEquals(0L, usernames);
		});
	}

	@DisplayName("A single account cannot own two subjects for the same provider")
	@ParameterizedTest(name = "{0}")
	@MethodSource("fixtures")
	void enforcesUniqueProviderLinkConstraint(DatabaseFixture fixture) {
		fixture.reset();
		UUID uniqueId = UUID.randomUUID();

		fixture.useHandle(handle -> {
			handle.execute(
					"INSERT INTO identica_accounts (unique_id, created_at, last_seen_at) VALUES (?, ?, ?)",
					uniqueId,
					1L,
					2L
			);
			handle.execute(
					"INSERT INTO identica_provider_links (unique_id, provider_id, provider_subject, is_primary, linked_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)",
					uniqueId,
					"provider",
					"subject-1",
					false,
					3L,
					4L
			);

			assertThrows(Exception.class, () -> handle.execute(
					"INSERT INTO identica_provider_links (unique_id, provider_id, provider_subject, is_primary, linked_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)",
					uniqueId,
					"provider",
					"subject-2",
					false,
					3L,
					4L
			));
		});
	}
}
