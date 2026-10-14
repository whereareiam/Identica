package me.whereareiam.identica.feature.verification.database;

import com.google.inject.util.Providers;
import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.adapter.database.testing.SimultaneousCalls;
import me.whereareiam.identica.adapter.database.testing.schema.TestDatabase;
import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import me.whereareiam.identica.feature.verification.database.entity.VerificationRecoveryCodeEntity;
import me.whereareiam.identica.feature.verification.database.entity.VerificationSelectionEntity;
import me.whereareiam.identica.feature.verification.database.repository.VerificationEnrollmentRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationRecoveryCodeRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationSelectionRepository;
import me.whereareiam.identica.feature.verification.model.VerificationRecoveryCode;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Verification persistence")
@Tag("database")
class DefaultVerificationPersistenceServiceTest {
	private static final int ROUNDS = 20;
	private static final int PROXIES = 4;

	@DisplayName("Stores, replaces and removes enrollments")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesEnrollments(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			service.upsertEnrollment(enrollment(uniqueId, "first", "secret", 10L));
			service.upsertEnrollment(enrollment(uniqueId, "second", "secret", 20L));
			service.upsertEnrollment(enrollment(uniqueId, "first", "changed", 30L));

			VerificationEnrollment latest = service.findEnrollment(uniqueId, "totp").orElseThrow();
			assertEquals("first", latest.getEnrollmentId());
			assertEquals("changed", latest.getCredential());
			assertEquals(30L, latest.getEnabledAt());
			assertEquals(2, service.findEnrollments(uniqueId).size());
			assertEquals(1, service.countEnrolledAccounts());

			service.deleteEnrollment(uniqueId, "totp");

			assertTrue(service.findEnrollments(uniqueId).isEmpty());
		});
	}

	@DisplayName("Accepts each counter of an enrollment once and never an older one")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void acceptsEachCounterOnce(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			service.upsertEnrollment(enrollment(uniqueId, "default", "secret", 10L).toBuilder().acceptedCounter(100L).build());
			service.upsertEnrollment(enrollment(uniqueId, "other", "secret", 5L));

			assertFalse(service.acceptCounter(uniqueId, "totp", "default", 100L), "the counter the enrollment started with");
			assertFalse(service.acceptCounter(uniqueId, "totp", "default", 99L));
			assertTrue(service.acceptCounter(uniqueId, "totp", "default", 102L));
			assertFalse(service.acceptCounter(uniqueId, "totp", "default", 102L));
			assertFalse(service.acceptCounter(uniqueId, "totp", "default", 101L));
			assertTrue(service.acceptCounter(uniqueId, "totp", "other", 101L), "each enrollment keeps its own counter");
			assertFalse(service.acceptCounter(uniqueId, "totp", "missing", 200L));

			assertEquals(102L, service.findEnrollments(uniqueId).stream()
					.filter(enrollment -> enrollment.getEnrollmentId().equals("default"))
					.findFirst().orElseThrow()
					.getAcceptedCounter());
		});
	}

	@DisplayName("Accepts a counter offered by several proxies at once exactly once")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void acceptsAConcurrentlyOfferedCounterOnce(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			service.upsertEnrollment(enrollment(uniqueId, "default", "secret", 10L));
			for (long counter = 1; counter <= 10; counter++) {
				long offered = counter;
				List<Boolean> offers = SimultaneousCalls.run(PROXIES, party ->
						service.acceptCounter(uniqueId, "totp", "default", offered));

				assertEquals(1, offers.stream().filter(Boolean::booleanValue).count(), "counter " + offered);
			}
		});
	}

	@DisplayName("Stores and replaces the selected method of a provider")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void storesSelections(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			service.upsertSelection(new VerificationSelection(uniqueId, "premium", "totp", 10L));
			service.upsertSelection(new VerificationSelection(uniqueId, "premium", "email", 20L));
			service.upsertSelection(new VerificationSelection(uniqueId, "credential", "totp", 30L));

			VerificationSelection premium = service.findSelection(uniqueId, "premium").orElseThrow();
			assertEquals("email", premium.getMethodId());
			assertEquals(20L, premium.getSelectedAt());
			assertEquals(2, service.findSelections(uniqueId).size());

			service.deleteSelection(uniqueId, "premium");
			service.deleteSelectionsByMethod(uniqueId, "totp");

			assertTrue(service.findSelections(uniqueId).isEmpty());
		});
	}

	@DisplayName("Uses a recovery code once")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void usesRecoveryCodesOnce(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			service.replaceRecoveryCodes(uniqueId, "totp", List.of(
					new VerificationRecoveryCode(uniqueId, "totp", "hash-a", 10L, 0L),
					new VerificationRecoveryCode(uniqueId, "totp", "hash-b", 10L, 0L)
			));

			assertTrue(service.markRecoveryCodeUsed(uniqueId, "totp", "hash-a", 20L));
			assertFalse(service.markRecoveryCodeUsed(uniqueId, "totp", "hash-a", 30L));
			assertEquals(20L, service.findRecoveryCodes(uniqueId, "totp").stream()
					.filter(code -> code.getCodeHash().equals("hash-a"))
					.findFirst().orElseThrow()
					.getUsedAt());

			service.replaceRecoveryCodes(uniqueId, "totp", List.of(new VerificationRecoveryCode(uniqueId, "totp", "hash-c", 40L, 0L)));

			assertEquals(List.of("hash-c"), service.findRecoveryCodes(uniqueId, "totp").stream().map(VerificationRecoveryCode::getCodeHash).toList());
		});
	}

	@DisplayName("Two proxies saving the same new enrollment at once both succeed, and one of their enrollments is kept")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void savesTheSameEnrollmentAtOnce(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			for (int round = 0; round < ROUNDS; round++) {
				String enrollmentId = "enrollment-" + round;
				List<String> written = List.of(enrollmentId + "-a", enrollmentId + "-b");

				SimultaneousCalls.run(2, party -> service.upsertEnrollment(enrollment(uniqueId, enrollmentId, written.get(party), 10L)));

				String kept = service.findEnrollments(uniqueId).stream()
						.filter(enrollment -> enrollment.getEnrollmentId().equals(enrollmentId))
						.findFirst().orElseThrow()
						.getCredential();
				assertTrue(written.contains(kept), "kept " + kept + " of " + written);
			}
			assertEquals(ROUNDS, service.findEnrollments(uniqueId).size());
		});
	}

	@DisplayName("Two proxies saving the same new selection at once both succeed, and one of their selections is kept")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void savesTheSameSelectionAtOnce(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			for (int round = 0; round < ROUNDS; round++) {
				String providerId = "provider-" + round;
				List<String> written = List.of("totp", "email");

				SimultaneousCalls.run(2, party -> service.upsertSelection(new VerificationSelection(uniqueId, providerId, written.get(party), 10L)));

				String kept = service.findSelection(uniqueId, providerId).orElseThrow().getMethodId();
				assertTrue(written.contains(kept), "kept " + kept + " of " + written);
			}
			assertEquals(ROUNDS, service.findSelections(uniqueId).size());
		});
	}

	@DisplayName("A recovery code used on several proxies at once is accepted on exactly one")
	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.identica.adapter.database.testing.schema.TestDatabases#all")
	void usesARecoveryCodeOnceAcrossProxies(TestDatabase database) {
		withVerification(database, (service, uniqueId) -> {
			List<VerificationRecoveryCode> codes = IntStream.range(0, ROUNDS)
					.mapToObj(round -> new VerificationRecoveryCode(uniqueId, "totp", "hash-" + round, 10L, 0L))
					.toList();
			service.replaceRecoveryCodes(uniqueId, "totp", codes);

			for (VerificationRecoveryCode code : codes) {
				List<Boolean> accepted = SimultaneousCalls.run(PROXIES, party ->
						service.markRecoveryCodeUsed(uniqueId, "totp", code.getCodeHash(), 100L + party));

				List<Integer> winners = IntStream.range(0, PROXIES).filter(accepted::get).boxed().toList();
				assertEquals(1, winners.size(), "accepted on " + winners);
				long usedAt = service.findRecoveryCodes(uniqueId, "totp").stream()
						.filter(stored -> stored.getCodeHash().equals(code.getCodeHash()))
						.findFirst().orElseThrow()
						.getUsedAt();
				assertEquals(100L + winners.getFirst(), usedAt);
			}
		});
	}

	private static void withVerification(TestDatabase database, Scenario scenario) {
		database.reset();
		DefaultDatabaseService databaseService = database.open();
		try {
			databaseService.getSchemaInstaller().install(new VerificationSchema());
			Jdbi jdbi = databaseService.getJdbi();
			UUID uniqueId = UUID.randomUUID();
			jdbi.useHandle(handle -> handle.createUpdate("INSERT INTO identica_accounts (unique_id, created_at, last_seen_at) VALUES (:uniqueId, 1, 1)")
					.bind("uniqueId", uniqueId)
					.execute());

			scenario.run(new DefaultVerificationPersistenceService(
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(VerificationEnrollmentEntity.class))),
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(VerificationSelectionEntity.class))),
					Providers.of(EntityAccess.of(jdbi, Dialectica.entity(VerificationRecoveryCodeEntity.class))),
					Providers.of(jdbi.onDemand(VerificationEnrollmentRepository.class)),
					Providers.of(jdbi.onDemand(VerificationSelectionRepository.class)),
					Providers.of(jdbi.onDemand(VerificationRecoveryCodeRepository.class))
			), uniqueId);
		} finally {
			TestDatabase.close(databaseService);
		}
	}

	private static VerificationEnrollment enrollment(UUID uniqueId, String enrollmentId, String credential, long enabledAt) {
		return new VerificationEnrollment(uniqueId, "totp", enrollmentId, credential, "Phone", 1L, enabledAt, 0L);
	}

	@FunctionalInterface
	private interface Scenario {
		void run(DefaultVerificationPersistenceService service, UUID uniqueId);
	}
}
