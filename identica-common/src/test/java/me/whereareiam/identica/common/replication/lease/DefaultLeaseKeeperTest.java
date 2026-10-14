package me.whereareiam.identica.common.replication.lease;

import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.LeaseRenewal;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.replication.lease.LeasedRecords;
import me.whereareiam.identica.replication.lease.Leases;
import me.whereareiam.identica.service.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Lease Keeper")
class DefaultLeaseKeeperTest {
	private static final long LIFETIME_MS = 30_000L;

	private final Scheduler scheduler = mock(Scheduler.class);
	private final Records records = new Records();
	private long now = 1_000_000L;
	private DefaultLeaseKeeper keeper;
	private Leases<String> leases;
	private PeriodicalRunnableTask renewal;

	@BeforeEach
	void start() {
		Replication replication = new Replication();
		replication.setHeartbeatTimeout(Duration.ofMillis(LIFETIME_MS));
		keeper = new DefaultLeaseKeeper(() -> replication, scheduler, () -> now);
		leases = keeper.leases("record", records);
		keeper.initialize();

		ArgumentCaptor<PeriodicalRunnableTask> scheduled = ArgumentCaptor.forClass(PeriodicalRunnableTask.class);
		verify(scheduler).schedule(scheduled.capture());
		renewal = scheduled.getValue();
	}

	@DisplayName("Held records are renewed every third of the heartbeat timeout")
	@Test
	void renewsEveryThirdOfTheLifetime() {
		assertEquals(LIFETIME_MS, keeper.lifetimeMs());
		assertEquals(LIFETIME_MS / 3, renewal.getPeriod());
		assertEquals(LIFETIME_MS / 3, renewal.getDelay());
	}

	@DisplayName("A renewal writes the stored record again for a full lifetime, keeping what another proxy changed")
	@Test
	void renewalKeepsTheStoredRecord() {
		hold("a", "mine");
		records.stored.put("a", "changed elsewhere");

		tick();

		Write write = records.only();
		assertEquals("changed elsewhere", write.record());
		assertEquals(1, write.renewal().getCount());
		assertFalse(write.renewal().isRestored());
		assertEquals(LIFETIME_MS, write.renewal().getTtlMs());
		assertEquals(Optional.of("changed elsewhere"), leases.find("a"));
	}

	@DisplayName("A record that is gone from the store is written again from the held copy")
	@Test
	void restoresAGoneRecordFromTheHeldCopy() {
		hold("a", "mine");
		records.stored.remove("a");

		tick();

		Write write = records.only();
		assertEquals("mine", write.record());
		assertTrue(write.renewal().isRestored());
	}

	@DisplayName("Each renewal counts on from the earlier ones, also when the record is held again with a new state")
	@Test
	void holdingAgainKeepsTheCount() {
		hold("a", "first");
		tick();

		hold("a", "second");
		tick();

		assertEquals(2, records.writes.size());
		assertEquals(2, records.writes.get(1).renewal().getCount());
		assertEquals("second", records.writes.get(1).record());
	}

	@DisplayName("A released record is neither renewed nor written again")
	@Test
	void releasedRecordIsLeftAlone() {
		hold("a", "mine");
		leases.release("a");

		tick();

		assertTrue(records.writes.isEmpty());
		assertFalse(leases.holds("a"));
		assertEquals(Optional.empty(), leases.find("a"));
	}

	@DisplayName("A record another owner took over ends its lease without being written")
	@Test
	void takenOverRecordEndsTheLease() {
		hold("a", "mine");
		records.stored.put("a", "theirs");
		records.owner = "theirs";

		tick();

		assertTrue(records.writes.isEmpty());
		assertFalse(leases.holds("a"));
	}

	@DisplayName("A lease with an end gives its record no lifetime beyond that end, and ends there")
	@Test
	void leaseWithAnEndStopsThere() {
		records.stored.put("a", "mine");
		leases.hold("a", "mine", now + LIFETIME_MS + 5_000L);

		tick();
		assertEquals(LIFETIME_MS, records.only().renewal().getTtlMs());

		now += 10_000L;
		tick();
		assertEquals(LIFETIME_MS - 5_000L, records.writes.get(1).renewal().getTtlMs());

		now += 25_000L;
		tick();
		assertEquals(2, records.writes.size());
		assertFalse(leases.holds("a"));
	}

	@DisplayName("Every kind of record is renewed on the one job, each through its own records")
	@Test
	void renewsEveryKindOnOneJob() {
		Records others = new Records();
		Leases<String> otherLeases = keeper.leases("other", others);
		hold("a", "mine");
		others.stored.put("b", "other");
		otherLeases.hold("b", "other");

		tick();

		assertEquals("mine", records.only().record());
		assertEquals("other", others.only().record());
	}

	@DisplayName("A failed renewal is tried again on the next one")
	@Test
	void failedRenewalIsTriedAgain() {
		hold("a", "mine");
		records.failing = true;

		tick();
		assertTrue(leases.holds("a"));

		records.failing = false;
		tick();
		assertEquals("mine", records.only().record());
	}

	@DisplayName("Shutting down stops the renewals and forgets the held records")
	@Test
	void shutdownStopsRenewing() {
		hold("a", "mine");

		keeper.shutdown();

		verify(scheduler).cancel(renewal.getKey());
		assertFalse(leases.holds("a"));
	}

	private void hold(String key, String record) {
		records.stored.put(key, record);
		leases.hold(key, record);
	}

	private void tick() {
		renewal.getRunnable().run();
	}

	private record Write(String key, String record, LeaseRenewal renewal) {
	}

	/**
	 * Records kept in a map, like a shared store. A held record is taken over once the store holds the record of
	 * {@link #owner}.
	 */
	private static final class Records implements LeasedRecords<String> {
		private final Map<String, String> stored = new ConcurrentHashMap<>();
		private final List<Write> writes = new ArrayList<>();
		private String owner;
		private boolean failing;

		@Override
		public @NotNull CompletableFuture<Optional<String>> find(@NotNull String key) {
			if (failing) return CompletableFuture.failedFuture(new IllegalStateException("unreachable"));

			return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(key)));
		}

		@Override
		public boolean isTakenOver(@NotNull String held, @NotNull String stored) {
			return Objects.equals(owner, stored);
		}

		@Override
		public @NotNull CompletableFuture<Void> write(@NotNull String key, @NotNull String record, @NotNull LeaseRenewal renewal) {
			writes.add(new Write(key, record, renewal));
			stored.put(key, record);
			return CompletableFuture.completedFuture(null);
		}

		private Write only() {
			assertEquals(1, writes.size(), writes.toString());
			return writes.getFirst();
		}
	}
}
