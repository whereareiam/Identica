package me.whereareiam.identica.replication.lease;

import me.whereareiam.identica.model.replication.LeaseRenewal;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * How one kind of leased record is read and written. The {@link LeaseKeeper} reads a held record before each renewal
 * and writes what it read, or the copy kept here when the record is gone; it never writes a record that was
 * released or taken over meanwhile.
 *
 * @param <T> record type
 */
public interface LeasedRecords<T> {
	/**
	 * Reads a record from the shared store, not from this proxy's copy.
	 *
	 * @param key key of the record
	 * @return the stored record, or empty when there is none
	 */
	@NotNull CompletableFuture<Optional<T>> find(@NotNull String key);

	/**
	 * Returns whether another owner took a record over, so this proxy no longer holds it and its lease ends without
	 * another write. By default a stored record is always the held one.
	 *
	 * @param held   this proxy's copy of the record
	 * @param stored the record as it is stored
	 * @return {@code true} when the stored record belongs to another owner
	 */
	default boolean isTakenOver(@NotNull T held, @NotNull T stored) {
		return false;
	}

	/**
	 * Writes a held record again, so that it lives for {@link LeaseRenewal#getTtlMs()} from now.
	 *
	 * @param key     key of the record
	 * @param record  the record to write: the stored one, or this proxy's copy when the record was gone
	 * @param renewal which renewal this is and how long the record lives afterwards
	 * @return completion of the write
	 */
	@NotNull CompletableFuture<Void> write(@NotNull String key, @NotNull T record, @NotNull LeaseRenewal renewal);
}
