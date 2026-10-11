package me.whereareiam.identica.common.prepare;

import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.replication.ReplicationAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Default Prepare State Store")
class DefaultPrepareStateStoreTest {
	private static final String CONNECTION_KEY = "Bob|127.0.0.1||";

	private final DefaultPrepareStateStore store = new DefaultPrepareStateStore(
			new DefaultReplicationSystem(localOnlyAdapter()),
			DefaultPrepareStateStoreTest::engine
	);

	@DisplayName("Clears a decision under its unique ID together with its connection key")
	@Test
	void clearByUniqueIdClearsConnectionKey() {
		UUID uniqueId = UUID.randomUUID();
		store.put(uniqueId, CONNECTION_KEY, PrepareDecision.deny("restricted"));

		assertTrue(store.clear(uniqueId));
		assertEquals(Optional.empty(), store.peek(uniqueId));
		assertEquals(Optional.empty(), store.peek(CONNECTION_KEY));
	}

	@DisplayName("Clears matching decisions under the unique ID and connection key")
	@Test
	void clearIfClearsBothKeys() {
		UUID uniqueId = UUID.randomUUID();
		store.put(uniqueId, CONNECTION_KEY, PrepareDecision.deny("restricted"));

		assertTrue(store.clearIf(uniqueId, CONNECTION_KEY, PrepareDecision::isDenied));
		assertEquals(Optional.empty(), store.peek(uniqueId));
		assertEquals(Optional.empty(), store.peek(CONNECTION_KEY));
		assertFalse(store.clearIf(uniqueId, CONNECTION_KEY, PrepareDecision::isDenied));
	}

	@DisplayName("Keeps a newer decision that does not match under the connection key")
	@Test
	void clearIfKeepsNewerDecisionUnderConnectionKey() {
		UUID uniqueId = UUID.randomUUID();
		store.put(uniqueId, CONNECTION_KEY, PrepareDecision.deny("restricted"));
		PrepareDecision handshake = PrepareDecision.allow();
		store.put(CONNECTION_KEY, handshake);

		assertTrue(store.clearIf(uniqueId, CONNECTION_KEY, PrepareDecision::isDenied));
		assertEquals(Optional.empty(), store.peek(uniqueId));
		assertSame(handshake, store.peek(CONNECTION_KEY).orElseThrow());
	}

	@DisplayName("Keeps decisions that do not match")
	@Test
	void clearIfKeepsDecisionsThatDoNotMatch() {
		UUID uniqueId = UUID.randomUUID();
		PrepareDecision allowed = PrepareDecision.allow();
		store.put(uniqueId, CONNECTION_KEY, allowed);

		assertFalse(store.clearIf(uniqueId, CONNECTION_KEY, PrepareDecision::isDenied));
		assertSame(allowed, store.peek(uniqueId).orElseThrow());
		assertSame(allowed, store.peek(CONNECTION_KEY).orElseThrow());
	}

	@DisplayName("Clears a matching decision under the connection key alone")
	@Test
	void clearIfWithoutUniqueId() {
		store.put(CONNECTION_KEY, PrepareDecision.deny("restricted"));

		assertTrue(store.clearIf(null, CONNECTION_KEY, PrepareDecision::isDenied));
		assertEquals(Optional.empty(), store.peek(CONNECTION_KEY));
	}

	private static Engine engine() {
		Engine engine = new Engine();
		Engine.Behavior behavior = new Engine.Behavior();
		behavior.setBridgeTtl(Duration.ofSeconds(30));
		engine.setBehavior(behavior);
		return engine;
	}

	private static ReplicationAdapter localOnlyAdapter() {
		ReplicationAdapter adapter = mock(ReplicationAdapter.class);
		when(adapter.isAvailable()).thenReturn(false);
		return adapter;
	}
}
