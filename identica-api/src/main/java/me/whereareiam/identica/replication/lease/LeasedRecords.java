package me.whereareiam.identica.replication.lease;

import me.whereareiam.identica.model.replication.LeaseRenewal;
import me.whereareiam.identica.replication.cache.Versioned;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * How one kind of leased record is read and written. The {@link LeaseKeeper} reads a held record before each renewal
 * and writes what it read, or the copy kept here when the record is gone; it never writes a record that was
 * released or taken over meanwhile. Between the read and the write another proxy may change or remove the record,
 * so a renewal hands back what it read, and a store that can writes only while the record is still as read.
 *
 * <pre>{@code
 * public CompletableFuture<Optional<Versioned<Holder>>> find(String key) {
 *     return cache.read(key);
 * }
 *
 * public CompletableFuture<Boolean> write(String key, Versioned<Holder> found, Holder holder, LeaseRenewal renewal) {
 *     return cache.write(key, found, holder, renewal.getTtlMs());
 * }
 * }</pre>
 *
 * @param <T> record type
 */
public interface LeasedRecords<T> {
	/**
	 * Reads a record from the shared store, not from this proxy's copy, together with the revision it was read at.
	 *
	 * @param key key of the record
	 * @return the stored record with its revision, or empty when there is none
	 */
	@NotNull CompletableFuture<Optional<Versioned<T>>> find(@NotNull String key);

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
	 * Writes a held record again, so that it lives for {@link LeaseRenewal#getTtlMs()} from now, but only while the
	 * record is still stored as the renewal found it: at the revision of {@code found}, or still absent when
	 * {@code found} is {@code null}. A record another proxy changed or removed meanwhile stays as that proxy left it.
	 * A store that cannot write conditionally may ignore {@code found} and write anyway.
	 *
	 * @param key     key of the record
	 * @param found   the record as the renewal read it, or {@code null} when it was gone
	 * @param record  the record to write: the stored one, or this proxy's copy when the record was gone
	 * @param renewal which renewal this is and how long the record lives afterwards
	 * @return {@code true} when written, {@code false} when the record changed since it was read
	 */
	@NotNull CompletableFuture<Boolean> write(
			@NotNull String key,
			@Nullable Versioned<T> found,
			@NotNull T record,
			@NotNull LeaseRenewal renewal
	);
}
