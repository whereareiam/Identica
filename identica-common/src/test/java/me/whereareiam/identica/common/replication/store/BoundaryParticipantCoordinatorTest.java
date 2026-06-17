package me.whereareiam.identica.common.replication.store;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("Boundary Participant Coordinator")
class BoundaryParticipantCoordinatorTest {
	@DisplayName("Coordinator dispatches only matching participant registries")
	@Test
	void coordinatorDispatchesOnlyMatchingParticipantRegistries() {
		DefaultScopedParticipantRegistry registry = new DefaultScopedParticipantRegistry();
		List<String> observed = new ArrayList<>();

		registry.disconnected().register(event -> observed.add("disconnected"));
		registry.completed().register(event -> observed.add("completed"));
		registry.terminated().register(event -> observed.add("terminated"));
		registry.accounts().register(event -> observed.add("account"));

		BoundaryParticipantCoordinator coordinator = new BoundaryParticipantCoordinator(
				registry.disconnected(),
				registry.completed(),
				registry.terminated(),
				registry.accounts(),
				mock(EventManager.class)
		);

		coordinator.onConnectionDisconnected(new ConnectionDisconnectedEvent(UUID.randomUUID(), null, null));
		coordinator.onConnectionCompleted(new ConnectionCompletedEvent(UUID.randomUUID(), null, null));
		coordinator.onConnectionTerminated(new ConnectionTerminatedEvent(UUID.randomUUID(), null, null));
		coordinator.onAccountLifecycle(new AccountLifecycleEvent(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", null)));

		assertEquals(List.of("disconnected", "completed", "terminated", "account"), observed);
	}

	@DisplayName("Registry preserves uniqueness and participant dispatch order")
	@Test
	void registryPreservesUniquenessAndDispatchOrder() {
		DefaultScopedParticipantRegistry registry = new DefaultScopedParticipantRegistry();
		List<String> order = new ArrayList<>();

		AccountLifecycleParticipant high = new AccountLifecycleParticipant() {
			@Override
			public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
				order.add("high");
			}

			@Override
			public @NotNull EventOrder order() {
				return EventOrder.HIGH;
			}
		};
		AccountLifecycleParticipant low = new AccountLifecycleParticipant() {
			@Override
			public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
				order.add("low");
			}

			@Override
			public @NotNull EventOrder order() {
				return EventOrder.LOW;
			}
		};
		AccountLifecycleParticipant lowest = new AccountLifecycleParticipant() {
			@Override
			public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
				order.add("lowest");
			}

			@Override
			public @NotNull EventOrder order() {
				return EventOrder.LOWEST;
			}
		};

		registry.accounts().register(high);
		registry.accounts().register(low);
		registry.accounts().register(low);
		registry.accounts().register(lowest);

		assertEquals(3, registry.accounts().values().size());
		AccountLifecycleEvent event = new AccountLifecycleEvent(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", null));
		for (AccountLifecycleParticipant participant : registry.accounts().values())
			participant.onAccountLifecycle(event);

		assertEquals(List.of("lowest", "low", "high"), order);
	}
}
