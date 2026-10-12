package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.provider.DefaultProviderLinkPersistenceService;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.repository.provider.ProviderLinkRepository;
import me.whereareiam.identica.adapter.database.testing.TestDataFactory;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UUIDs and booleans are stored as they were before Identica's Jdbi spoke the Dialectica dialect,
 * when Jdbi bound them itself: rows written either way hold the same values and read back either
 * way.
 */
@DisplayName("Stored value compatibility")
class StoredValueCompatibilityIntegrationTest {
	@DisplayName("Stores UUIDs and booleans as Jdbi's own binding did")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesTheSameValues(TestDatabase database) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			UUID before = UUID.randomUUID();
			UUID now = UUID.randomUUID();
			writeAsBefore(plainJdbi(service), before, "Before");
			writeAsNow(service.getJdbi(), now, "Now");

			List<Object> stored = database.connect(connection -> List.of(stored(connection, "Before"), stored(connection, "Now")));

			StoredLink storedBefore = (StoredLink) stored.get(0);
			StoredLink storedNow = (StoredLink) stored.get(1);
			assertEquals(storedBefore.uniqueId().getClass(), storedNow.uniqueId().getClass(), "UUID column type");
			assertEquals(before.toString(), String.valueOf(storedBefore.uniqueId()));
			assertEquals(now.toString(), String.valueOf(storedNow.uniqueId()));
			assertEquals(storedBefore.primary(), storedNow.primary(), "boolean column value");
		} finally {
			TestDatabase.close(service);
		}
	}

	@DisplayName("Reads rows that Jdbi's own binding wrote")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void readsRowsWrittenBefore(TestDatabase database) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			UUID uniqueId = UUID.randomUUID();
			writeAsBefore(plainJdbi(service), uniqueId, "Before");
			Jdbi jdbi = service.getJdbi();

			Account account = accounts(jdbi).findByUniqueId(uniqueId).orElseThrow();
			assertEquals("Before", account.getUsername());
			assertEquals(List.of(uniqueId), accounts(jdbi).findByUsername("before").stream().map(Account::getUniqueId).toList());
			AccountProviderLink link = links(jdbi).findBySubject("example", "Before").orElseThrow();
			assertEquals(uniqueId, link.getUniqueId());
			assertTrue(link.isPrimaryLink());
			assertEquals(List.of(uniqueId), links(jdbi).findByUniqueId(uniqueId).stream().map(AccountProviderLink::getUniqueId).toList());
		} finally {
			TestDatabase.close(service);
		}
	}

	@DisplayName("Writes rows that Jdbi's own binding reads")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void writesRowsReadableAsBefore(TestDatabase database) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			UUID uniqueId = UUID.randomUUID();
			writeAsNow(service.getJdbi(), uniqueId, "Now");

			AccountProviderLinkEntity link = plainJdbi(service).withHandle(handle -> handle.createQuery("""
							SELECT unique_id AS uniqueId,
							       provider_id AS providerId,
							       provider_subject AS providerSubject,
							       is_primary AS primaryLink,
							       linked_at AS linkedAt,
							       last_seen_at AS lastSeenAt
							  FROM identica_provider_links
							 WHERE unique_id = :uniqueId
							""")
					.bind("uniqueId", uniqueId)
					.mapToBean(AccountProviderLinkEntity.class)
					.one());
			assertEquals(uniqueId, link.getUniqueId());
			assertTrue(link.isPrimaryLink());
		} finally {
			TestDatabase.close(service);
		}
	}

	/**
	 * A Jdbi on Identica's database that binds and maps values as Identica's did before it installed
	 * the Dialectica plugin.
	 */
	private static Jdbi plainJdbi(DefaultDatabaseService service) {
		return Jdbi.create(service.getDataSource()).installPlugin(new SqlObjectPlugin());
	}

	private static void writeAsBefore(Jdbi jdbi, UUID uniqueId, String username) {
		jdbi.useHandle(handle -> {
			handle.createUpdate("""
							INSERT INTO identica_accounts (unique_id, username, created_at, last_seen_at)
							VALUES (:uniqueId, :username, :createdAt, :lastSeenAt)
							""")
					.bind("uniqueId", uniqueId)
					.bind("username", username)
					.bind("createdAt", TestDataFactory.CREATED_AT)
					.bind("lastSeenAt", TestDataFactory.LAST_SEEN_AT)
					.execute();
			handle.createUpdate("""
							INSERT INTO identica_provider_links (
								unique_id, provider_id, provider_subject, is_primary, linked_at, last_seen_at
							) VALUES (
								:uniqueId, :providerId, :providerSubject, :primary, :linkedAt, :lastSeenAt
							)
							""")
					.bind("uniqueId", uniqueId)
					.bind("providerId", "example")
					.bind("providerSubject", username)
					.bind("primary", true)
					.bind("linkedAt", TestDataFactory.LINKED_AT)
					.bind("lastSeenAt", TestDataFactory.LAST_SEEN_AT)
					.execute();
		});
	}

	private static void writeAsNow(Jdbi jdbi, UUID uniqueId, String username) {
		accounts(jdbi).create(TestDataFactory.account(uniqueId, username));
		links(jdbi).upsert(TestDataFactory.providerLink(uniqueId, "example", username, true));
	}

	private static StoredLink stored(Connection connection, String subject) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT unique_id, is_primary FROM identica_provider_links WHERE provider_subject = ?")) {
			statement.setString(1, subject);
			try (ResultSet rows = statement.executeQuery()) {
				assertTrue(rows.next(), "the link of " + subject);
				return new StoredLink(rows.getObject("unique_id"), rows.getObject("is_primary"));
			}
		}
	}

	private static DefaultAccountPersistenceService accounts(Jdbi jdbi) {
		return new DefaultAccountPersistenceService(
				EntityAccess.of(jdbi, Dialectica.entity(AccountEntity.class)),
				jdbi.onDemand(AccountRepository.class),
				Mockito.mock(EventManager.class)
		);
	}

	private static DefaultProviderLinkPersistenceService links(Jdbi jdbi) {
		return new DefaultProviderLinkPersistenceService(
				EntityAccess.of(jdbi, Dialectica.entity(AccountProviderLinkEntity.class)),
				jdbi.onDemand(ProviderLinkRepository.class),
				Mockito.mock(EventManager.class)
		);
	}

	/**
	 * The values of a provider link's UUID and boolean columns, as the driver returns them.
	 */
	private record StoredLink(Object uniqueId, Object primary) {
	}
}
