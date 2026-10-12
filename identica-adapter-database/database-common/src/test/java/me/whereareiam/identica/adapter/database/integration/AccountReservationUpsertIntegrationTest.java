package me.whereareiam.identica.adapter.database.integration;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.account.DefaultAccountReservationPersistenceService;
import me.whereareiam.identica.adapter.database.entity.account.AccountReservationEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountReservationRepository;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Account reservation upsert")
class AccountReservationUpsertIntegrationTest {
	private static final long CREATED_AT = 1_000L;
	private static final long EXPIRES_AT = Long.MAX_VALUE / 2;

	@DisplayName("Reserves a key that is not reserved yet")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void insertsANewReservation(TestDatabase database) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			DefaultAccountReservationPersistenceService reservations = reservations(jdbi);
			UUID uniqueId = UUID.randomUUID();

			reservations.reserve("example:alice", uniqueId, CREATED_AT, EXPIRES_AT);

			assertEquals(Optional.of(uniqueId), reservations.find("example:alice"));
			assertEquals(1, count(jdbi));
		} finally {
			TestDatabase.close(service);
		}
	}

	@DisplayName("Replaces every column of a reserved key and leaves other keys alone")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void replacesAnExistingReservation(TestDatabase database) {
		database.reset();
		DefaultDatabaseService service = database.open();
		try {
			Jdbi jdbi = service.getJdbi();
			DefaultAccountReservationPersistenceService reservations = reservations(jdbi);
			EntityAccess<AccountReservationEntity> rows = EntityAccess.of(jdbi, Dialectica.entity(AccountReservationEntity.class));
			UUID first = UUID.randomUUID();
			UUID second = UUID.randomUUID();
			UUID other = UUID.randomUUID();
			reservations.reserve("example:alice", first, CREATED_AT, EXPIRES_AT);
			reservations.reserve("example:bob", other, CREATED_AT, EXPIRES_AT);

			reservations.reserve("example:alice", second, CREATED_AT + 1, EXPIRES_AT - 1);

			AccountReservationEntity replaced = rows.find("example:alice").orElseThrow();
			assertEquals(second, replaced.getUniqueId());
			assertEquals(CREATED_AT + 1, replaced.getCreatedAt());
			assertEquals(EXPIRES_AT - 1, replaced.getExpiresAt());
			assertEquals(Optional.of(other), reservations.find("example:bob"));
			assertEquals(2, count(jdbi));
		} finally {
			TestDatabase.close(service);
		}
	}

	private static DefaultAccountReservationPersistenceService reservations(Jdbi jdbi) {
		return new DefaultAccountReservationPersistenceService(
				EntityAccess.of(jdbi, Dialectica.entity(AccountReservationEntity.class)),
				jdbi.onDemand(AccountReservationRepository.class)
		);
	}

	private static long count(Jdbi jdbi) {
		return jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM identica_account_reservations")
				.mapTo(Long.class)
				.one());
	}
}
