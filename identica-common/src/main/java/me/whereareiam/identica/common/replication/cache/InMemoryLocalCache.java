package me.whereareiam.identica.common.replication.cache;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.replication.cache.LocalCache;
import me.whereareiam.identica.replication.cache.Versioned;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

	/**
	 * Reads an entry together with its revision: the entry object itself, which every write replaces.
	 */
	public @NotNull Optional<Versioned<T>> read(@Nullable String key) {
		Entry<T> entry = live(key);
		if (entry == null || entry.value == null) return Optional.empty();

		return Optional.of(new Versioned<>(entry.value, entry));
	}

	/**
	 * Writes an entry only while it is still the entry that was read, or while there is none when {@code read} is
	 * {@code null}. The map compares and replaces in one atomic step.
	 */
	public boolean write(@Nullable String key, @Nullable Versioned<T> read, @NotNull T value, long ttlMs) {
		if (key == null || ttlMs <= 0) return false;

		Entry<T> next = new Entry<>(value, System.currentTimeMillis() + ttlMs);
		Entry<T> current = live(key);
		if (read == null) {
			if (current != null) return false;
			return entries.putIfAbsent(key, next) == null;
		}

		return current != null && current == read.getRevision() && entries.replace(key, current, next);
	}

	/**
	 * Removes an entry only while it is still the entry that was read.
	 */
	public boolean remove(@Nullable String key, @NotNull Versioned<T> read) {
		if (key == null) return false;

		Entry<T> current = live(key);
		return current != null && current == read.getRevision() && entries.remove(key, current);
	}

	/**
	 * Returns the entry under a key, removing it when it expired.
	 */
	private @Nullable Entry<T> live(@Nullable String key) {
		if (key == null) return null;

		Entry<T> entry = entries.get(key);
		if (entry == null) return null;
		if (entry.expiresAt > 0 && entry.expiresAt <= System.currentTimeMillis()) {
			entries.remove(key, entry);
			return null;
		}

		return entry;
	}

	@RequiredArgsConstructor
	private static final class Entry<T> {
		private final T value;
		private final long expiresAt;
	}
}
