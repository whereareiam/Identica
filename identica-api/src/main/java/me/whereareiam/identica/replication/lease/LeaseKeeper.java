package me.whereareiam.identica.replication.lease;

import org.jetbrains.annotations.NotNull;

/**
 * Keeps the shared records this proxy owns alive for as long as it owns them, and no longer than the proxy itself
 * lives. A leased record is written with the lease lifetime, {@code replication.heartbeatTimeout}, and this proxy
 * renews every record it holds each {@link #RENEWALS_PER_LIFETIME}th of that time. A record that is released, or
 * whose proxy stopped or crashed without releasing it, is not renewed and expires within one lifetime.
 * <p>
 * The proxy holding a lease is the authority on its record: a renewal keeps a change another proxy made to the
 * stored record, writes the copy kept here again when the stored record is gone, for example after the shared store
 * was unreachable, and ends the lease when another owner took the record over.
 * <p>
 * Each kind of record gets its own {@link Leases}, which says how its records are read and written:
 *
 * <pre>{@code
 * Leases<Holder> holds = leaseKeeper.leases("username-holds", new HolderRecords(cache));
 * cache.put(key, holder, Math.min(leaseKeeper.lifetimeMs(), endsAt - System.currentTimeMillis())).join();
 * holds.hold(key, holder, endsAt);
 * }</pre>
 */
public interface LeaseKeeper {
	/**
	 * Renewals within one lease lifetime. A record survives two failed renewals in a row.
	 */
	int RENEWALS_PER_LIFETIME = 3;

	/**
	 * Returns how long a leased record lives without being renewed, in milliseconds: the time after which the
	 * records of a proxy that stopped without releasing them disappear.
	 *
	 * @return the lease lifetime in milliseconds
	 */
	long lifetimeMs();

	/**
	 * Creates the leases of one kind of record. The keeper renews them until they are released or it shuts down.
	 *
	 * @param name    name of the kind of record, used in log messages, such as {@code sessions}
	 * @param records how the records are read and written
	 * @param <T>     record type
	 * @return the leases of that kind of record, none of them held yet
	 */
	@NotNull <T> Leases<T> leases(@NotNull String name, @NotNull LeasedRecords<T> records);
}
