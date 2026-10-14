package me.whereareiam.identica.replication.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * A value as it was read from a {@link ReplicatedCache}, together with the revision it was read at. Handing it back
 * to {@link ReplicatedCache#write(String, Versioned, Object, long)} or {@link ReplicatedCache#remove(String, Versioned)}
 * changes the entry only while it still holds exactly what was read.
 *
 * <pre>{@code
 * Versioned<Session> read = cache.read(key).join().orElseThrow();
 * boolean written = cache.write(key, read, changed(read.getValue()), ttlMs).join();
 * }</pre>
 *
 * @param <T> value type
 */
@Getter
@ToString(of = "value")
@RequiredArgsConstructor
public final class Versioned<T> {
	/**
	 * The value that was read.
	 */
	private final @NotNull T value;
	/**
	 * Opaque revision of the entry the value was read from; only the cache that produced it interprets it.
	 */
	private final @NotNull Object revision;
}
