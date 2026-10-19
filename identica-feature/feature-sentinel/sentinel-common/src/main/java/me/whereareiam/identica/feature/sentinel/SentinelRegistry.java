package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.Registry;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The sentinels of one kind that are registered: the definitions that count attempts, or the quotas.
 *
 * @param <T> the kind of sentinel
 */
public class SentinelRegistry<T> implements Registry<T> {
	private final Set<T> values = new CopyOnWriteArraySet<>();

	@Override
	public void register(T value) {
		if (value == null) return;
		values.add(value);
	}

	@Override
	public void unregister(T value) {
		if (value == null) return;
		values.remove(value);
	}

	@Override
	public Set<T> values() {
		return Collections.unmodifiableSet(values);
	}
}
