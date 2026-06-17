package me.whereareiam.identica.replication.cache.base;

import me.whereareiam.identica.model.replication.ReplicationPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 * Generic key/value cache abstraction for replicated or local caches.
 *
 * @param <T> value type
 */
public interface Cache<T> {
	/**
	 * Retrieves a cached entry.
	 *
	 * @param key cache key
	 * @return optional cached value
	 */
	@NotNull CompletableFuture<Optional<T>> get(@Nullable String key);

	/**
	 * Retrieves a cached entry by UUID key.
	 *
	 * @param key cache key
	 * @return optional cached value
	 */
	@NotNull
	default CompletableFuture<Optional<T>> get(@NotNull UUID key) {
		return get(key.toString());
	}

	/**
	 * Retrieves a cached entry while bypassing stale local replicas when supported.
	 *
	 * <p>The default implementation delegates to {@link #get(String)}.</p>
	 *
	 * @param key cache key
	 * @return optional cached value
	 */
	@NotNull
	default CompletableFuture<Optional<T>> getFresh(@Nullable String key) {
		return get(key);
	}

	/**
	 * Retrieves a cached entry by UUID key while bypassing stale local replicas
	 * when supported.
	 *
	 * @param key cache key
	 * @return optional cached value
	 */
	@NotNull
	default CompletableFuture<Optional<T>> getFresh(@NotNull UUID key) {
		return getFresh(key.toString());
	}

	/**
	 * Stores a cached entry.
	 *
	 * @param key cache key
	 * @param value cached value
	 * @param ttlMs time-to-live in milliseconds
	 * @return completion journey
	 */
	@NotNull CompletableFuture<Void> put(@Nullable String key, @Nullable T value, long ttlMs);

	/**
	 * Stores a cached entry by UUID key.
	 *
	 * @param key cache key
	 * @param value cached value
	 * @param ttlMs time-to-live in milliseconds
	 * @return completion journey
	 */
	@NotNull
	default CompletableFuture<Void> put(@NotNull UUID key, @Nullable T value, long ttlMs) {
		return put(key.toString(), value, ttlMs);
	}

	/**
	 * Stores a cached entry using the cache's configured default TTL.
	 *
	 * @param key cache key
	 * @param value cached value
	 * @return completion journey
	 */
	@NotNull
	default CompletableFuture<Void> put(@Nullable String key, @Nullable T value) {
		return put(key, value, defaultTtlMs());
	}

	/**
	 * Stores a cached entry by UUID key using the cache's configured default TTL.
	 *
	 * @param key cache key
	 * @param value cached value
	 * @return completion journey
	 */
	@NotNull
	default CompletableFuture<Void> put(@NotNull UUID key, @Nullable T value) {
		return put(key.toString(), value, defaultTtlMs());
	}

	/**
	 * Returns the cache's configured default TTL in milliseconds.
	 *
	 * @return default TTL in milliseconds, or {@code 0} when unset
	 */
	default long defaultTtlMs() {
		return 0L;
	}

	/**
	 * Invalidates a cached entry.
	 *
	 * @param key cache key
	 * @return completion journey
	 */
	@NotNull CompletableFuture<Void> invalidate(@Nullable String key);

	/**
	 * Invalidates a cached entry by UUID key.
	 *
	 * @param key cache key
	 * @return completion journey
	 */
	@NotNull
	default CompletableFuture<Void> invalidate(@NotNull UUID key) {
		return invalidate(key.toString());
	}

	/**
	 * Atomically retrieves and invalidates a cached entry when supported by the implementation.
	 *
	 * <p>The default implementation falls back to {@link #get(String)} followed by
	 * {@link #invalidate(String)}.</p>
	 *
	 * @param key cache key
	 * @return optional consumed value
	 */
	@NotNull
	default CompletableFuture<Optional<T>> consume(@Nullable String key) {
		return get(key).thenCompose(value ->
				invalidate(key).thenApply(ignored -> value)
		);
	}

	/**
	 * Atomically retrieves and invalidates a cached entry by UUID key when
	 * supported by the implementation.
	 *
	 * @param key cache key
	 * @return optional consumed value
	 */
	@NotNull
	default CompletableFuture<Optional<T>> consume(@NotNull UUID key) {
		return consume(key.toString());
	}

	/**
	 * Lists keys in this cache namespace.
	 *
	 * @param page page number (1-based)
	 * @param pageSize number of entries per page
	 * @return page of keys
	 */
	@NotNull
	default CompletableFuture<ReplicationPage> listKeys(int page, int pageSize) {
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, pageSize);
		return CompletableFuture.completedFuture(ReplicationPage.empty(safePage, safeSize));
	}
}
