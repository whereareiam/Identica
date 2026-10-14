package me.whereareiam.identica.replication.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import me.whereareiam.identica.replication.cache.base.Cache;

/**
 * Cache whose entries are shared by every proxy through the replication adapter.
 *
 * <p>Replicated caches fall back to local behavior when the adapter is unavailable. Besides plain reads and writes,
 * they offer conditional ones: {@link #read(String)} returns a value with its revision, and {@link #write} or
 * {@link #remove} with that revision only succeed while the entry is unchanged since. The check and the change are
 * one atomic step, in the shared store and locally alike, so of two writers that read the same entry only the first
 * to write succeeds and the other learns that it read a stale value.</p>
 *
 * @param <T> value type
 */
public interface ReplicatedCache<T> extends Cache<T> {
	/**
	 * Reads an entry from the shared store, not from this proxy's copy, together with its revision.
	 *
	 * @param key entry key
	 * @return the value with its revision, or empty when there is no entry
	 */
	@NotNull CompletableFuture<Optional<Versioned<T>>> read(@Nullable String key);

	/**
	 * Writes an entry only while it is still as it was read.
	 *
	 * @param key entry key
	 * @param read what the writer read: the entry must still be at this revision, or, when {@code null}, there must
	 *             be no entry
	 * @param value value to write
	 * @param ttlMs time-to-live in milliseconds
	 * @return {@code true} when written, {@code false} when the entry changed since it was read
	 */
	@NotNull CompletableFuture<Boolean> write(@Nullable String key, @Nullable Versioned<T> read, @NotNull T value, long ttlMs);

	/**
	 * Writes an entry with the cache's default time-to-live only while it is still as it was read.
	 *
	 * @see #write(String, Versioned, Object, long)
	 */
	default @NotNull CompletableFuture<Boolean> write(@Nullable String key, @Nullable Versioned<T> read, @NotNull T value) {
		return write(key, read, value, defaultTtlMs());
	}

	/**
	 * Removes an entry only while it is still as it was read.
	 *
	 * @param key entry key
	 * @param read what the remover read
	 * @return {@code true} when removed, {@code false} when the entry changed or was removed since it was read
	 */
	@NotNull CompletableFuture<Boolean> remove(@Nullable String key, @NotNull Versioned<T> read);
}
