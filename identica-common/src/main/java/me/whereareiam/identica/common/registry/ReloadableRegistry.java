package me.whereareiam.identica.common.registry;

import com.google.inject.Provider;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.config.ConfigLoadOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ReloadableRegistry implements Provider<Set<Reloadable>>, Registry<Reloadable> {
	private final Set<Reloadable> reloadables = new HashSet<>();

	@Override
	public void register(Reloadable value) {
		reloadables.add(value);
	}

	@Override
	public void unregister(Reloadable value) {
		reloadables.remove(value);
	}

	/**
	 * Returns the registered components in the order they are reloaded, which is the order documents are
	 * loaded in at startup: a document before the extensions of it.
	 */
	@Override
	public Set<Reloadable> values() {
		List<Reloadable> ordered = new ArrayList<>(reloadables);
		ordered.sort(Comparator.comparing(reloadable -> (Class<?>) reloadable.getClass(), ConfigLoadOrder.BY_TYPE));

		return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
	}

	@Override
	public Set<Reloadable> get() {
		return values();
	}
}
