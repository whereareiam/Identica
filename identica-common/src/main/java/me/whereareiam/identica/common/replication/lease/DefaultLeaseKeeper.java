package me.whereareiam.identica.common.replication.lease;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.LeaseRenewal;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.replication.lease.LeaseKeeper;
import me.whereareiam.identica.replication.lease.LeasedRecords;
import me.whereareiam.identica.replication.lease.Leases;
import me.whereareiam.identica.service.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Renews every lease this proxy holds on one scheduled job, every third of {@code replication.heartbeatTimeout}.
 * The lifetime is read once, on first use; a changed heartbeat timeout applies once Identica starts again.
 */
@Singleton
public class DefaultLeaseKeeper implements LeaseKeeper, RuntimeLifecycle {
	private static final Origin ORIGIN = Origin.core(DefaultLeaseKeeper.class);
	private static final JobKey JOB_KEY = JobKey.of(ORIGIN, Purpose.of("lease-renewal"), "renew");
	private static final long UNLIMITED = Long.MAX_VALUE;

	private final Supplier<Long> lifetimeMs;
	private final Scheduler scheduler;
	private final LongSupplier clock;
	private final List<HeldRecords<?>> kinds = new CopyOnWriteArrayList<>();

	@Inject
	public DefaultLeaseKeeper(@NotNull Provider<Replication> replicationProvider, @NotNull Scheduler scheduler) {
		this(replicationProvider, scheduler, System::currentTimeMillis);
	}

	DefaultLeaseKeeper(@NotNull Provider<Replication> replicationProvider, @NotNull Scheduler scheduler, @NotNull LongSupplier clock) {
		this.lifetimeMs = Suppliers.memoize(() -> replicationProvider.get().heartbeatTimeoutMillis());
		this.scheduler = scheduler;
		this.clock = clock;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	/**
	 * Starts renewing the held records every third of a lease lifetime.
	 */
	@Override
	public void initialize() {
		long interval = lifetimeMs() / RENEWALS_PER_LIFETIME;
		scheduler.schedule(PeriodicalRunnableTask.builder()
				.key(JOB_KEY)
				.delay(interval)
				.period(interval)
				.runnable(this::renew)
				.build());
	}

	/**
	 * Stops renewing and forgets the held records, which expire within one lease lifetime unless their holders
	 * removed them.
	 */
	@Override
	public void shutdown() {
		scheduler.cancel(JOB_KEY);
		for (HeldRecords<?> kind : kinds)
			kind.held.clear();
	}

	@Override
	public long lifetimeMs() {
		return lifetimeMs.get();
	}

	@Override
	public @NotNull <T> Leases<T> leases(@NotNull String name, @NotNull LeasedRecords<T> records) {
		HeldRecords<T> kind = new HeldRecords<>(name, records);
		kinds.add(kind);

		return kind;
	}

	private void renew() {
		for (HeldRecords<?> kind : kinds)
			kind.renew();
	}

	/**
	 * The records of one kind this proxy holds, with what each renewal needs: the copy kept here, when its lease
	 * ends and how often it was renewed.
	 */
	private final class HeldRecords<T> implements Leases<T> {
		private final String name;
		private final LeasedRecords<T> records;
		private final Map<String, Held<T>> held = new ConcurrentHashMap<>();

		private HeldRecords(@NotNull String name, @NotNull LeasedRecords<T> records) {
			this.name = name;
			this.records = records;
		}

		@Override
		public void hold(@NotNull String key, @NotNull T record) {
			hold(key, record, UNLIMITED);
		}

		@Override
		public void hold(@NotNull String key, @NotNull T record, long endsAt) {
			held.compute(key, (ignored, current) -> new Held<>(record, endsAt, current != null ? current.renewals : 0));
		}

		@Override
		public void release(@NotNull String key) {
			held.remove(key);
		}

		@Override
		public boolean holds(@NotNull String key) {
			return held.containsKey(key);
		}

		@Override
		public @NotNull Optional<T> find(@NotNull String key) {
			return Optional.ofNullable(held.get(key)).map(Held::record);
		}

		private void renew() {
			for (String key : held.keySet())
				renew(key);
		}

		/**
		 * Writes the stored record again, so a change another proxy made to it is kept, or the copy kept here when
		 * the record is gone. A lease that was released, taken over or ended meanwhile is not written.
		 */
		private void renew(@NotNull String key) {
			records.find(key)
					.thenCompose(stored -> {
						Held<T> renewed = held.computeIfPresent(key, (ignored, current) -> next(current, stored));
						if (renewed == null) return CompletableFuture.<Void>completedFuture(null);

						long ttlMs = ttlMs(renewed.endsAt);
						if (ttlMs <= 0) return end(key, renewed);

						return records.write(key, renewed.record, new LeaseRenewal(renewed.renewals, stored.isEmpty(), ttlMs));
					})
					.exceptionally(failure -> {
						Logger.debug("Could not renew the lease of %s %s: %s", name, key, failure.getMessage());
						return null;
					});
		}

		/**
		 * Returns the lease after one more renewal, or {@code null} to end it when another owner took the record
		 * over.
		 */
		private @Nullable Held<T> next(@NotNull Held<T> current, @NotNull Optional<T> stored) {
			if (stored.isPresent() && records.isTakenOver(current.record, stored.get())) return null;

			return new Held<>(stored.orElse(current.record), current.endsAt, current.renewals + 1);
		}

		/**
		 * Ends a lease that ran out; its record expires at that moment by the time to live of its last write.
		 */
		private @NotNull CompletableFuture<Void> end(@NotNull String key, @NotNull Held<T> lease) {
			held.remove(key, lease);
			return CompletableFuture.completedFuture(null);
		}

		private long ttlMs(long endsAt) {
			long lifetimeMs = lifetimeMs();
			if (endsAt == UNLIMITED) return lifetimeMs;

			return Math.min(lifetimeMs, endsAt - clock.getAsLong());
		}
	}

	private record Held<T>(@NotNull T record, long endsAt, int renewals) {
	}
}
