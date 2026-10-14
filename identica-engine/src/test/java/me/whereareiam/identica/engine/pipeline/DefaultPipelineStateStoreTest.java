package me.whereareiam.identica.engine.pipeline;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.StateKey;
import me.whereareiam.identica.pipeline.state.StoredPipelineState;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.util.EventUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Default Pipeline-State Store")
class DefaultPipelineStateStoreTest {
	private static final String OWNER_ADDRESS = "127.0.0.1";
	private static final String OTHER_ADDRESS = "127.0.0.2";

	@DisplayName("Stores and finds a state under the connection that saved it")
	@Test
	void findsTheStateOfTheSameConnection() {
		DefaultPipelineStateStore store = localStore();
		PipelineStateReference owner = PipelineStateReference.of("Alice", OWNER_ADDRESS);

		store.save(owner, pendingState(), 1_000L);

		assertTrue(store.find(owner).isPresent());
	}

	@DisplayName("A connection with the same username from another address finds no state")
	@Test
	void anotherAddressFindsNothing() {
		DefaultPipelineStateStore store = localStore();
		store.save(PipelineStateReference.of("Alice", OWNER_ADDRESS), pendingState(), 1_000L);

		assertTrue(store.find(PipelineStateReference.of("Alice", OTHER_ADDRESS)).isEmpty());
		assertTrue(store.consume(PipelineStateReference.of("Alice", OTHER_ADDRESS)).isEmpty());
	}

	@DisplayName("Saving and clearing the state of another address leaves the owner's state as it was")
	@Test
	void anotherAddressLeavesTheOwnersStateAlone() {
		DefaultPipelineStateStore store = localStore();
		PipelineStateReference owner = PipelineStateReference.of("Alice", OWNER_ADDRESS);
		PipelineStateReference other = PipelineStateReference.of("Alice", OTHER_ADDRESS);
		store.save(owner, pendingState(), 1_000L);

		PipelineState fresh = PipelineState.initial();
		fresh.setPipelineType(PipelineType.AUTHENTICATION);
		store.save(other, fresh, 1_000L);
		store.clear(other);

		Optional<PipelineState> found = store.find(owner);
		assertTrue(found.isPresent());
		assertEquals(PipelineType.REGISTRATION, found.get().getPipelineType());
	}

	@DisplayName("Consuming a state removes it")
	@Test
	void consumingRemovesTheState() {
		DefaultPipelineStateStore store = localStore();
		PipelineStateReference owner = PipelineStateReference.of("Alice", OWNER_ADDRESS);
		store.save(owner, pendingState(), 1_000L);

		assertTrue(store.consume(owner).isPresent());
		assertTrue(store.find(owner).isEmpty());
	}

	@DisplayName("Loads pipeline state with a scenario context from the replication adapter")
	@Test
	void findsStateStoredThroughAnAvailableAdapter() {
		EventUtil.initialize(mock(EventManager.class));
		ReplicationAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = PipelineStateReference.of("Alice", OWNER_ADDRESS);
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "Alice", OWNER_ADDRESS))
				.intendedServer("lobby")
				.build());

		new DefaultPipelineStateStore(new DefaultReplicationSystem(shared), this::replication).save(reference, state, 60_000L);
		Optional<PipelineState> found = new DefaultPipelineStateStore(new DefaultReplicationSystem(shared), this::replication)
				.find(reference);

		assertTrue(found.isPresent());
		assertEquals(PipelineType.REGISTRATION, found.get().getPipelineType());
		assertEquals("Alice", found.get().getScenario().getIdentity().getUsername());
	}

	private PipelineState pendingState() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.put(JourneyStateItem.KEY, new JourneyStateItem(null, null, 0), 1_000L);
		return state;
	}

	@DisplayName("Never writes values of run keys to the replication adapter")
	@Test
	void saveLeavesRunValuesOut() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = PipelineStateReference.of("Alice", OWNER_ADDRESS);
		PipelineState state = pendingState();
		state.put(TypedPassword.KEY, new TypedPassword("Secret-123"), 60_000L);

		new DefaultPipelineStateStore(new DefaultReplicationSystem(shared), this::replication).save(reference, state, 60_000L);
		Optional<PipelineState> found = new DefaultPipelineStateStore(new DefaultReplicationSystem(shared), this::replication)
				.find(reference);

		assertTrue(shared.values.values().stream()
				.noneMatch(value -> new String(value, StandardCharsets.UTF_8).contains("Secret-123")));
		assertTrue(found.isPresent());
		assertTrue(found.get().has(JourneyStateItem.KEY));
		assertTrue(found.get().get(TypedPassword.KEY).isEmpty());
		assertTrue(state.has(TypedPassword.KEY), "saving changes the state it was given");
	}

	@DisplayName("Discards a saved state whose scenario cannot be read back")
	@Test
	void discardsAnUnreadableState() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = PipelineStateReference.of("Alice", OWNER_ADDRESS);
		StoredPipelineState stored = pendingState().store();
		stored.getValues().put(RegistrationContext.KEY.id(), new StoredPipelineState.StoredValue("{\"identity\": 1}", 0L));
		DefaultPipelineStateStore store = new DefaultPipelineStateStore(new DefaultReplicationSystem(shared), this::replication);

		store.save(reference, PipelineState.restore(stored), 60_000L);

		assertTrue(store.find(reference).isEmpty());
		assertTrue(shared.values.isEmpty(), "the unreadable state is removed");
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static final class TypedPassword {
		private static final StateKey<TypedPassword> KEY = StateKey.run("test:typed-password", TypedPassword.class);

		private String password;
	}

	private static final class InMemoryAdapter implements ReplicationAdapter {
		private final Map<String, byte[]> values = new ConcurrentHashMap<>();

		public boolean isAvailable() { return true; }

		public CompletableFuture<Optional<byte[]>> get(String namespace, String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.get(namespace + ":" + key)));
		}

		public CompletableFuture<Optional<byte[]>> consume(String namespace, String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.remove(namespace + ":" + key)));
		}

		public CompletableFuture<Void> put(String namespace, String key, byte[] value, long ttlMs) {
			values.put(namespace + ":" + key, value);
			return CompletableFuture.completedFuture(null);
		}

		public CompletableFuture<Void> invalidate(String namespace, String key) {
			values.remove(namespace + ":" + key);
			return CompletableFuture.completedFuture(null);
		}

		public CompletableFuture<Void> publish(String channel, byte[] payload) {
			return CompletableFuture.completedFuture(null);
		}

		public void subscribe(String channel, Consumer<byte[]> handler) { }
	}

	private Replication replication() {
		Replication replication = new Replication();
		Replication.Cache cache = new Replication.Cache();
		cache.setPipelineState("pipeline-state");
		replication.setCache(cache);
		return replication;
	}

	private DefaultPipelineStateStore localStore() {
		EventUtil.initialize(mock(EventManager.class));
		return new DefaultPipelineStateStore(new DefaultReplicationSystem(localOnlyAdapter()), this::replication);
	}

	private ReplicationAdapter localOnlyAdapter() {
		ReplicationAdapter adapter = mock(ReplicationAdapter.class);
		when(adapter.isAvailable()).thenReturn(false);
		return adapter;
	}
}
