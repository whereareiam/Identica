package me.whereareiam.identica.feature.verification.database;

import me.whereareiam.dialectica.DialectPlugin;
import me.whereareiam.identica.feature.verification.database.entity.VerificationTimeStepEntity;
import me.whereareiam.identica.feature.verification.database.repository.VerificationEnrollmentRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationRecoveryCodeRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationSelectionRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationTimeStepRepository;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The last accepted time step of one-time codes, on the databases proxies can share. PostgreSQL and MariaDB run
 * only when Docker is available.
 */
@DisplayName("Verification time steps")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerificationTimeStepPersistenceTest {
	private final List<JdbcDatabaseContainer<?>> containers = new ArrayList<>();
	private final List<Database> databases = new ArrayList<>();

	@BeforeAll
	void startDatabases() {
		databases.add(new Database("H2", Jdbi.create("jdbc:h2:mem:verification;DB_CLOSE_DELAY=-1")));
		if (!DockerClientFactory.instance().isDockerAvailable()) return;

		PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");
		MariaDBContainer mariaDb = new MariaDBContainer("mariadb:12");
		containers.add(postgres);
		containers.add(mariaDb);
		containers.forEach(JdbcDatabaseContainer::start);
		databases.add(new Database("POSTGRES", jdbi(postgres)));
		databases.add(new Database("MYSQL", jdbi(mariaDb)));
	}

	@AfterAll
	void stopDatabases() {
		containers.forEach(JdbcDatabaseContainer::stop);
	}

	List<Database> databases() {
		return databases;
	}

	@DisplayName("Accepts each time step once and never an older one")
	@ParameterizedTest(name = "{0}")
	@MethodSource("databases")
	void acceptsEachStepOnceAndNeverAnOlderOne(Database database) {
		UUID uniqueId = database.account();
		VerificationPersistenceService service = database.service();

		assertTrue(service.acceptTimeStep(uniqueId, "totp", 100));
		assertFalse(service.acceptTimeStep(uniqueId, "totp", 100));
		assertFalse(service.acceptTimeStep(uniqueId, "totp", 99));
		assertTrue(service.acceptTimeStep(uniqueId, "totp", 101));
		assertFalse(service.acceptTimeStep(uniqueId, "totp", 101));
	}

	@DisplayName("Keeps the last step per method and forgets it with the enrollment")
	@ParameterizedTest(name = "{0}")
	@MethodSource("databases")
	void keepsTheLastStepPerMethodAndForgetsItWithTheEnrollment(Database database) {
		UUID uniqueId = database.account();
		VerificationPersistenceService service = database.service();

		assertTrue(service.acceptTimeStep(uniqueId, "totp", 100));
		assertTrue(service.acceptTimeStep(uniqueId, "other", 100));

		service.deleteEnrollment(uniqueId, "totp");

		assertTrue(service.acceptTimeStep(uniqueId, "totp", 100));
		assertFalse(service.acceptTimeStep(uniqueId, "other", 100));
	}

	@DisplayName("Of concurrent first and later submissions of the same step only one is accepted")
	@ParameterizedTest(name = "{0}")
	@MethodSource("databases")
	void acceptsTheSameStepSubmittedConcurrentlyOnlyOnce(Database database) throws Exception {
		VerificationPersistenceService service = database.service();
		UUID first = database.account();
		UUID later = database.account();
		assertTrue(service.acceptTimeStep(later, "totp", 99));

		assertEquals(1, concurrently(() -> service.acceptTimeStep(first, "totp", 100)));
		assertEquals(1, concurrently(() -> service.acceptTimeStep(later, "totp", 100)));
	}

	private static int concurrently(@NotNull Callable<Boolean> submission) throws Exception {
		int submissions = 8;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(submissions);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < submissions; i++)
				results.add(executor.submit(() -> {
					start.await();
					return submission.call();
				}));
			start.countDown();

			int accepted = 0;
			for (Future<Boolean> result : results)
				if (result.get()) accepted++;
			return accepted;
		} finally {
			executor.shutdownNow();
		}
	}

	private static Jdbi jdbi(JdbcDatabaseContainer<?> container) {
		return Jdbi.create(container.getJdbcUrl(), container.getUsername(), container.getPassword());
	}

	/**
	 * A database holding the accounts table the time steps refer to and the time step table itself.
	 */
	static final class Database {
		private final String type;
		private final Jdbi jdbi;

		Database(String type, Jdbi jdbi) {
			this.type = type;
			this.jdbi = jdbi.installPlugin(new SqlObjectPlugin()).installPlugin(new DialectPlugin(type));
			String uuidType = "MYSQL".equals(type) ? "CHAR(36)" : "UUID";
			this.jdbi.useHandle(handle -> {
				handle.execute("CREATE TABLE IF NOT EXISTS identica_accounts (unique_id " + uuidType + " PRIMARY KEY)");
				handle.execute(new VerificationTimeStepEntity().statement(type));
			});
		}

		UUID account() {
			UUID uniqueId = UUID.randomUUID();
			jdbi.useHandle(handle -> handle.execute("INSERT INTO identica_accounts (unique_id) VALUES (?)", uniqueId));
			return uniqueId;
		}

		VerificationPersistenceService service() {
			return new DefaultVerificationPersistenceService(
					mock(VerificationEnrollmentRepository.class),
					mock(VerificationSelectionRepository.class),
					mock(VerificationRecoveryCodeRepository.class),
					jdbi.onDemand(VerificationTimeStepRepository.class)
			);
		}

		@Override
		public String toString() {
			return type;
		}
	}
}
