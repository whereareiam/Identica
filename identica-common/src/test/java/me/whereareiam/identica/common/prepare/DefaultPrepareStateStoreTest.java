package me.whereareiam.identica.common.prepare;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Default Prepare State Store")
class DefaultPrepareStateStoreTest {
	@DisplayName("Disconnect boundary clears prepare state by unique id and indexed connection key")
	@Test
	void disconnectBoundaryClearsPrepareState() {
		DefaultPrepareStateStore store = new DefaultPrepareStateStore(
				new DefaultReplicationSystem(localOnlyAdapter()),
				this::settings,
				noopRegistry()
		);
		UUID uniqueId = UUID.randomUUID();
		String connectionKey = "PlayerOne|127.0.0.1||";
		PrepareDecision decision = PrepareDecision.allow();
		store.put(uniqueId, connectionKey, decision);

		store.onConnectionDisconnected(new ConnectionDisconnectedEvent(uniqueId, null, null));

		assertTrue(store.peek(uniqueId).isEmpty());
		assertTrue(store.peek(connectionKey).isEmpty());
	}

	private Engine settings() {
		Engine settings = new Engine();
		Engine.Behavior behavior = new Engine.Behavior();
		behavior.setBridgeTtl(Duration.ofSeconds(30));
		settings.setBehavior(behavior);
		return settings;
	}

	private ReplicationAdapter localOnlyAdapter() {
		ReplicationAdapter adapter = mock(ReplicationAdapter.class);
		when(adapter.isAvailable()).thenReturn(false);
		return adapter;
	}

	private Registry<ConnectionDisconnectedParticipant> noopRegistry() {
		return new Registry<>() {
			@Override
			public void register(ConnectionDisconnectedParticipant value) {
			}

			@Override
			public void unregister(ConnectionDisconnectedParticipant value) {
			}

			@Override
			public Set<ConnectionDisconnectedParticipant> values() {
				return Set.of();
			}
		};
	}
}
