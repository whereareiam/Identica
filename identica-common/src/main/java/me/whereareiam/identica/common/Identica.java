package me.whereareiam.identica.common;

import com.google.inject.Inject;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaBootstrappedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Starts and stops Identica: the bound {@link RuntimeLifecycle}s, phase by phase in the order of
 * {@link StartupPhase}, and within a phase in the order they were bound. They stop in the reverse order.
 */
public class Identica implements EventListener {
	private final EventManager eventManager;
	private final List<RuntimeLifecycle> lifecycles;
	private final List<RuntimeLifecycle> started = new ArrayList<>();

	@Inject
	public Identica(@NotNull EventManager eventManager, @NotNull Set<RuntimeLifecycle> lifecycles) {
		this.eventManager = eventManager;
		this.lifecycles = lifecycles.stream()
				.sorted(Comparator.comparing(RuntimeLifecycle::phase))
				.toList();

		eventManager.register(this);
	}

	/**
	 * Starts every lifecycle. {@link IdenticaBootstrappedEvent} is called once the features have started and
	 * {@link IdenticaReadyEvent} at the end. When a lifecycle fails, the ones started before it, and the failed
	 * one itself, are stopped in reverse and the failure is passed on.
	 */
	public void start() {
		try {
			for (StartupPhase phase : StartupPhase.values()) {
				for (RuntimeLifecycle lifecycle : lifecycles) {
					if (lifecycle.phase() != phase) continue;

					started.add(lifecycle);
					lifecycle.initialize();
				}
				if (phase == StartupPhase.FEATURES) eventManager.call(new IdenticaBootstrappedEvent());
			}
		} catch (RuntimeException | Error failure) {
			stop();
			throw failure;
		}

		eventManager.call(new IdenticaReadyEvent());
	}

	@IdenticEvent(EventOrder.LOW)
	public void onShutdown(IdenticaShutdownEvent event) {
		stop();
	}

	private void stop() {
		for (int i = started.size() - 1; i >= 0; i--) {
			RuntimeLifecycle lifecycle = started.get(i);
			try {
				lifecycle.shutdown();
			} catch (RuntimeException failure) {
				Logger.warn(failure, "Failed to stop %s", lifecycle.getClass().getSimpleName());
			}
		}
		started.clear();
	}
}
