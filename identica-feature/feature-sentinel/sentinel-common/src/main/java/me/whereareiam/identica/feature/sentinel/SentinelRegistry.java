package me.whereareiam.identica.feature.sentinel;

import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
public class SentinelRegistry implements Registry<SentinelDefinition> {
	private final Set<SentinelDefinition> definitions = new CopyOnWriteArraySet<>();

	@Override
	public void register(SentinelDefinition value) {
		if (value == null) return;
		definitions.add(value);
	}

	@Override
	public void unregister(SentinelDefinition value) {
		if (value == null) return;
		definitions.remove(value);
	}

	@Override
	public Set<SentinelDefinition> values() {
		return Collections.unmodifiableSet(definitions);
	}
}
