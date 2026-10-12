package me.whereareiam.identica.addon.dialogs.trigger;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registered triggers, started with the sink they report to and stopped when removed or when the addon stops.
 */
public final class TriggerRegistry {
	private final TriggerSink sink;
	private final Map<String, DialogTrigger> triggers = new LinkedHashMap<>();

	/**
	 * Creates the registry.
	 *
	 * @param sink where every trigger reports
	 */
	public TriggerRegistry(@NotNull TriggerSink sink) {
		this.sink = sink;
	}

	/**
	 * Adds and starts a trigger.
	 *
	 * @param trigger the trigger
	 * @throws IllegalArgumentException when a trigger with the same ID is registered
	 */
	public synchronized void register(@NotNull DialogTrigger trigger) {
		if (triggers.putIfAbsent(trigger.id(), trigger) != null)
			throw new IllegalArgumentException("A dialog trigger with the ID " + trigger.id() + " is already registered");

		trigger.start(sink);
	}

	/**
	 * Stops and removes a trigger.
	 *
	 * @param trigger the trigger
	 */
	public synchronized void unregister(@NotNull DialogTrigger trigger) {
		if (triggers.remove(trigger.id(), trigger)) trigger.stop();
	}

	/**
	 * Stops and removes every trigger.
	 */
	public void stopAll() {
		List<DialogTrigger> stopped;
		synchronized (this) {
			stopped = List.copyOf(triggers.values());
			triggers.clear();
		}
		stopped.forEach(DialogTrigger::stop);
	}
}
