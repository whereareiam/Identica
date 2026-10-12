package me.whereareiam.identica.common;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DisplayName("Identica start-up")
class IdenticaTest {
	private final List<String> journal = new ArrayList<>();
	private final EventManager events = recordingEvents();

	@DisplayName("Starts every phase before the next, in binding order within a phase, and calls the lifecycle events between them")
	@Test
	void startsPhaseByPhase() {
		Identica identica = identica(
				lifecycle("database", StartupPhase.PERSISTENCE),
				lifecycle("providers", StartupPhase.PROVIDERS),
				lifecycle("configuration", StartupPhase.CONFIGURATION),
				lifecycle("username", StartupPhase.RUNTIME),
				lifecycle("connections", StartupPhase.CONNECTIONS),
				lifecycle("logging", StartupPhase.LOGGING),
				lifecycle("sessions", StartupPhase.RUNTIME),
				lifecycle("statistics", StartupPhase.INTEGRATIONS),
				lifecycle("features", StartupPhase.FEATURES)
		);

		identica.start();

		assertEquals(List.of(
				"start configuration",
				"start logging",
				"start database",
				"start username",
				"start sessions",
				"start features",
				"IdenticaBootstrappedEvent",
				"start providers",
				"start statistics",
				"start connections",
				"IdenticaReadyEvent"
		), journal);
	}

	@DisplayName("Stops the lifecycles in the reverse order of their start when Identica shuts down")
	@Test
	void stopsInReverse() {
		Identica identica = identica(
				lifecycle("logging", StartupPhase.LOGGING),
				lifecycle("database", StartupPhase.PERSISTENCE),
				lifecycle("username", StartupPhase.RUNTIME),
				lifecycle("sessions", StartupPhase.RUNTIME),
				lifecycle("providers", StartupPhase.PROVIDERS)
		);
		identica.start();
		journal.clear();

		identica.onShutdown(new IdenticaShutdownEvent());
		identica.onShutdown(new IdenticaShutdownEvent());

		assertEquals(List.of(
				"stop providers",
				"stop sessions",
				"stop username",
				"stop database",
				"stop logging"
		), journal);
	}

	@DisplayName("A failed lifecycle stops itself and the ones started before it in reverse, and the startup fails")
	@Test
	void failureStopsTheStartedLifecyclesInReverse() {
		IllegalStateException failure = new IllegalStateException("database refused");
		Identica identica = identica(
				lifecycle("configuration", StartupPhase.CONFIGURATION),
				lifecycle("logging", StartupPhase.LOGGING),
				failing("database", StartupPhase.PERSISTENCE, failure),
				lifecycle("username", StartupPhase.RUNTIME),
				lifecycle("connections", StartupPhase.CONNECTIONS)
		);

		assertSame(failure, assertThrows(IllegalStateException.class, identica::start));

		assertEquals(List.of(
				"start configuration",
				"start logging",
				"start database",
				"stop database",
				"stop logging",
				"stop configuration"
		), journal);
	}

	private Identica identica(RuntimeLifecycle... lifecycles) {
		return new Identica(events, new LinkedHashSet<>(List.of(lifecycles)));
	}

	private RuntimeLifecycle lifecycle(String name, StartupPhase phase) {
		return failing(name, phase, null);
	}

	private RuntimeLifecycle failing(String name, StartupPhase phase, RuntimeException failure) {
		return new RuntimeLifecycle() {
			@Override
			public @NotNull StartupPhase phase() {
				return phase;
			}

			@Override
			public void initialize() {
				journal.add("start " + name);
				if (failure != null) throw failure;
			}

			@Override
			public void shutdown() {
				journal.add("stop " + name);
			}
		};
	}

	private EventManager recordingEvents() {
		EventManager events = mock(EventManager.class);
		doAnswer(invocation -> {
			Event event = invocation.getArgument(0);
			journal.add(event.getClass().getSimpleName());
			return null;
		}).when(events).call(any(Event.class));
		return events;
	}
}
