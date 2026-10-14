package me.whereareiam.identica.replication.lease;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The records of one kind that this proxy holds, created by {@link LeaseKeeper#leases}. Holding a record keeps it
 * renewed; the first write is the holder's own, made before or right after it starts holding. Releasing a record
 * only stops renewing it, so it expires within one lease lifetime unless the holder removes it.
 *
 * @param <T> record type
 */
public interface Leases<T> {
	/**
	 * Starts renewing a record, for as long as this proxy holds it. When the record is held already, its new state
	 * is taken over and renewed from then on.
	 *
	 * @param key    key of the record
	 * @param record the record as this proxy wrote it
	 */
	void hold(@NotNull String key, @NotNull T record);

	/**
	 * Starts renewing a record until a moment at the latest. Each renewal gives the record the lease lifetime, but
	 * never beyond that moment, at which the lease ends by itself and the record expires.
	 *
	 * @param key    key of the record
	 * @param record the record as this proxy wrote it
	 * @param endsAt epoch milliseconds at which the lease ends
	 */
	void hold(@NotNull String key, @NotNull T record, long endsAt);

	/**
	 * Stops renewing a record, which then expires within one lease lifetime unless the holder removes it.
	 *
	 * @param key key of the record
	 */
	void release(@NotNull String key);

	/**
	 * Returns whether this proxy holds a record.
	 *
	 * @param key key of the record
	 * @return {@code true} while the record is renewed here
	 */
	boolean holds(@NotNull String key);

	/**
	 * Returns this proxy's copy of a record it holds, as it was last written or read back by a renewal.
	 *
	 * @param key key of the record
	 * @return the held record, or empty when this proxy does not hold it
	 */
	@NotNull Optional<T> find(@NotNull String key);
}
