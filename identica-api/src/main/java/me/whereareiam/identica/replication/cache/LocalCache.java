package me.whereareiam.identica.replication.cache;

import me.whereareiam.identica.replication.cache.base.Cache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Marker interface for in-memory caches.
 *
 * <p>Local caches never use the replication adapter.</p>
 *
 * @param <T> value type
 */
public interface LocalCache<T> extends Cache<T> {
	/**
	 * Removes a cached entry only if its value matches the condition. Checking and removing happen as one step,
	 * so a value stored under the key in between is never removed. An entry that does not match keeps its value
	 * and its expiry.
	 *
	 * @param key       cache key
	 * @param condition what the value must match to be removed
	 * @return removed value, or empty when the entry is missing, expired or does not match
	 */
	@NotNull CompletableFuture<Optional<T>> consumeIf(@Nullable String key, @NotNull Predicate<? super T> condition);
}
