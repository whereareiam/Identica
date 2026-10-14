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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	@DisplayName("A writer whose state another writer saved meanwhile is refused, and the other writer's change stays")
	@Test
	void staleSaveIsRefused() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		DefaultPipelineStateStore proxyA = store(shared);
		DefaultPipelineStateStore proxyB = store(shared);
		PipelineStateReference reference = connection();
		assertTrue(proxyA.save(reference, pendingState(), 60_000L));

		PipelineState readByA = proxyA.find(reference).orElseThrow();
		PipelineState readByB = proxyB.find(reference).orElseThrow();
		readByB.put(Mark.KEY, new Mark("b"), 60_000L);
		assertTrue(proxyB.save(reference, readByB, 60_000L));
		readByA.put(Mark.KEY, new Mark("a"), 60_000L);

		assertFalse(proxyA.save(reference, readByA, 60_000L));
		assertEquals("b", mark(proxyA, reference));
	}

	@DisplayName("A saved state can be saved again by the writer that saved it")
	@Test
	void savedStateCarriesItsNewRevision() {
		EventUtil.initialize(mock(EventManager.class));
		DefaultPipelineStateStore store = store(new InMemoryAdapter());
		PipelineStateReference reference = connection();
		assertTrue(store.save(reference, pendingState(), 60_000L));
		PipelineState read = store.find(reference).orElseThrow();

		assertTrue(store.save(reference, read, 60_000L));
		read.put(Mark.KEY, new Mark("again"), 60_000L);
		assertTrue(store.save(reference, read, 60_000L));
		assertEquals("again", mark(store, reference));
	}

	@DisplayName("A writer whose state was consumed meanwhile does not bring it back")
	@Test
	void saveAfterConsumeIsRefused() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		DefaultPipelineStateStore proxyA = store(shared);
		PipelineStateReference reference = connection();
		proxyA.save(reference, pendingState(), 60_000L);
		PipelineState readByA = proxyA.find(reference).orElseThrow();

		PipelineState consumed = store(shared).consume(reference).orElseThrow();

		assertNull(consumed.getRevision());
		assertFalse(proxyA.save(reference, readByA, 60_000L));
		assertTrue(proxyA.find(reference).isEmpty());
	}

	@DisplayName("Of two consumers at the same moment only one receives the state")
	@Test
	void stateIsConsumedOnce() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		store(shared).save(reference, pendingState(), 60_000L);
		List<Optional<PipelineState>> other = new ArrayList<>();
		shared.beforeCompare = () -> {
			shared.beforeCompare = () -> {};
			other.add(store(shared).consume(reference));
		};

		Optional<PipelineState> first = store(shared).consume(reference);

		assertTrue(other.getFirst().isPresent());
		assertTrue(first.isEmpty());
	}

	@DisplayName("A consumed state saves again only while no other writer created a state meanwhile")
	@Test
	void consumedStateIsCreatedOnlyWhileNoneIsStored() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		store(shared).save(reference, pendingState(), 60_000L);
		PipelineState consumed = store(shared).consume(reference).orElseThrow();
		markFresh(store(shared), reference, "other");

		consumed.put(Mark.KEY, new Mark("mine"), 60_000L);

		assertFalse(store(shared).save(reference, consumed, 60_000L));
		assertEquals("other", mark(store(shared), reference));
	}

	@DisplayName("A writer that is done clears only the state it read, not a newer one")
	@Test
	void clearKeepsANewerState() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		store(shared).save(reference, pendingState(), 60_000L);
		PipelineState read = store(shared).find(reference).orElseThrow();
		PipelineState newer = store(shared).find(reference).orElseThrow();
		newer.put(Mark.KEY, new Mark("newer"), 60_000L);
		assertTrue(store(shared).save(reference, newer, 60_000L));

		assertFalse(store(shared).clear(reference, read));
		assertEquals("newer", mark(store(shared), reference));

		assertTrue(store(shared).clear(reference, store(shared).find(reference).orElseThrow()));
		assertTrue(store(shared).find(reference).isEmpty());
	}

	@DisplayName("A state without a revision is not saved over a stored one")
	@Test
	void saveWithoutRevisionKeepsAStoredState() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		markFresh(store(shared), reference, "first");

		PipelineState second = pendingState();
		second.put(Mark.KEY, new Mark("second"), 60_000L);

		assertFalse(store(shared).save(reference, second, 60_000L));
		assertEquals("first", mark(store(shared), reference));
	}

	@DisplayName("Of two writers that both found no state, only the first creates one")
	@Test
	void creatorsOfOneConnectionOnlyOneWins() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		shared.beforeCompare = () -> {
			shared.beforeCompare = () -> {};
			markFresh(store(shared), reference, "other");
		};
		PipelineState mine = pendingState();
		mine.put(Mark.KEY, new Mark("mine"), 60_000L);

		assertFalse(store(shared).save(reference, mine, 60_000L));
		assertNull(mine.getRevision());
		assertEquals("other", mark(store(shared), reference));
	}

	@DisplayName("A writer that read the stored state before starting over replaces it")
	@Test
	void saveOverTheStateItStartedOver() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		store(shared).save(reference, pendingState(), 60_000L);
		PipelineState fresh = pendingState();
		fresh.put(Mark.KEY, new Mark("fresh"), 60_000L);
		fresh.setRevision(store(shared).find(reference).orElseThrow().getRevision());

		assertTrue(store(shared).save(reference, fresh, 60_000L));
		assertEquals("fresh", mark(store(shared), reference));
	}

	@DisplayName("A writer that found no state clears nothing another writer saved")
	@Test
	void clearWithoutRevisionKeepsAStoredState() {
		EventUtil.initialize(mock(EventManager.class));
		InMemoryAdapter shared = new InMemoryAdapter();
		PipelineStateReference reference = connection();
		store(shared).save(reference, pendingState(), 60_000L);

		assertFalse(store(shared).clear(reference, pendingState()));
		assertTrue(store(shared).find(reference).isPresent());
		assertTrue(store(shared).clear(PipelineStateReference.of("Bob", OWNER_ADDRESS), pendingState()));
	}

	@DisplayName("Without Redis, a stale save is refused on the proxy's own cache as well")
	@Test
	void staleSaveIsRefusedLocally() {
		DefaultPipelineStateStore store = localStore();
		PipelineStateReference reference = connection();
		assertTrue(store.save(reference, pendingState(), 60_000L));
		PipelineState stale = store.find(reference).orElseThrow();
		PipelineState newer = store.find(reference).orElseThrow();
		newer.put(Mark.KEY, new Mark("newer"), 60_000L);
		assertTrue(store.save(reference, newer, 60_000L));

		stale.put(Mark.KEY, new Mark("stale"), 60_000L);

		assertFalse(store.save(reference, stale, 60_000L));
		assertEquals("newer", mark(store, reference));
	}

	/** Creates a state the way a writer that found none does. */
	private void markFresh(DefaultPipelineStateStore store, PipelineStateReference reference, String mark) {
		PipelineState state = pendingState();
		state.put(Mark.KEY, new Mark(mark), 60_000L);
		assertTrue(store.save(reference, state, 60_000L));
	}

	private static String mark(DefaultPipelineStateStore store, PipelineStateReference reference) {
		return store.find(reference).orElseThrow().get(Mark.KEY).orElseThrow().getValue();
	}

	private DefaultPipelineStateStore store(InMemoryAdapter shared) {
		return new DefaultPipelineStateStore(new DefaultReplicationSystem(shared), this::replication);
	}

	private static PipelineStateReference connection() {
		return PipelineStateReference.of("Alice", OWNER_ADDRESS);
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static final class Mark {
		private static final StateKey<Mark> KEY = StateKey.persisted("test:mark", Mark.class);

		private String value;
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
		/** Runs between a writer's read and its conditional write, to let another writer in. */
		private Runnable beforeCompare = () -> {};

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

		public CompletableFuture<Boolean> compareAndPut(String namespace, String key, byte[] expected, byte[] value, long ttlMs) {
			beforeCompare.run();
			boolean[] written = {false};
			values.compute(namespace + ":" + key, (ignored, current) -> {
				written[0] = expected == null ? current == null : current != null && Arrays.equals(current, expected);
				return written[0] ? value : current;
			});
			return CompletableFuture.completedFuture(written[0]);
		}

		public CompletableFuture<Boolean> compareAndInvalidate(String namespace, String key, byte[] expected) {
			beforeCompare.run();
			boolean[] removed = {false};
			values.computeIfPresent(namespace + ":" + key, (ignored, current) -> {
				removed[0] = Arrays.equals(current, expected);
				return removed[0] ? null : current;
			});
			return CompletableFuture.completedFuture(removed[0]);
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
