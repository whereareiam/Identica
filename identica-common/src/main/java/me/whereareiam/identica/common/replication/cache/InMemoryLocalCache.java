package me.whereareiam.identica.common.replication.cache;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.replication.cache.LocalCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class InMemoryLocalCache<T> implements LocalCache<T> {
	private final ConcurrentHashMap<String, Entry<T>> entries = new ConcurrentHashMap<>();
	private final long defaultTtlMs;

	public InMemoryLocalCache() {
		this(0L);
	}

	public InMemoryLocalCache(long defaultTtlMs) {
		this.defaultTtlMs = Math.max(0L, defaultTtlMs);
	}

	@Override
	public @NotNull CompletableFuture<Optional<T>> get(String key) {
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());

		Entry<T> entry = entries.get(key);
		if (entry == null) return CompletableFuture.completedFuture(Optional.empty());
		if (entry.expiresAt > 0 && entry.expiresAt <= System.currentTimeMillis()) {
			entries.remove(key, entry);
			return CompletableFuture.completedFuture(Optional.empty());
		}

		return CompletableFuture.completedFuture(Optional.ofNullable(entry.value));
	}

	@Override
	public @NotNull CompletableFuture<Void> put(String key, T value, long ttlMs) {
		if (key == null) return CompletableFuture.completedFuture(null);
		if (ttlMs <= 0) {
			entries.remove(key);
			return CompletableFuture.completedFuture(null);
		}

		long expiresAt = System.currentTimeMillis() + ttlMs;
		entries.put(key, new Entry<>(value, expiresAt));

		return CompletableFuture.completedFuture(null);
	}

	@Override
	public long defaultTtlMs() {
		return defaultTtlMs;
	}

	@Override
	public @NotNull CompletableFuture<Void> invalidate(String key) {
		if (key != null) entries.remove(key);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public @NotNull CompletableFuture<Optional<T>> consume(String key) {
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());

		Entry<T> entry = entries.remove(key);
		if (entry == null) return CompletableFuture.completedFuture(Optional.empty());
		if (entry.expiresAt > 0 && entry.expiresAt <= System.currentTimeMillis())
			return CompletableFuture.completedFuture(Optional.empty());

		return CompletableFuture.completedFuture(Optional.ofNullable(entry.value));
	}

	@Override
	public @NotNull CompletableFuture<Optional<T>> consumeIf(String key, @NotNull Predicate<? super T> condition) {
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());

		AtomicReference<T> removed = new AtomicReference<>();
		entries.computeIfPresent(key, (ignored, entry) -> {
			if (entry.expiresAt > 0 && entry.expiresAt <= System.currentTimeMillis()) return null;
			if (!condition.test(entry.value)) return entry;

			removed.set(entry.value);
			return null;
		});

		return CompletableFuture.completedFuture(Optional.ofNullable(removed.get()));
	}

	@Override
	public @NotNull CompletableFuture<ReplicationPage> listKeys(int page, int pageSize) {
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, pageSize);

		long now = System.currentTimeMillis();
		List<String> keys = new ArrayList<>();

		for (Map.Entry<String, Entry<T>> entry : entries.entrySet()) {
			String key = entry.getKey();
			Entry<T> value = entry.getValue();
			if (key == null || value == null) continue;
			if (value.expiresAt > 0 && value.expiresAt <= now) {
				entries.remove(key, value);
				continue;
			}
			keys.add(key);
		}

		keys.sort(String::compareTo);
		int total = keys.size();
		int fromIndex = Math.min((safePage - 1) * safeSize, total);
		int toIndex = Math.min(fromIndex + safeSize, total);

		List<String> pageKeys = fromIndex < toIndex
				? keys.subList(fromIndex, toIndex)
				: List.of();

		return CompletableFuture.completedFuture(new ReplicationPage(pageKeys, safePage, safeSize, total));
	}

	@RequiredArgsConstructor
	private static final class Entry<T> {
		private final T value;
		private final long expiresAt;
	}
}
