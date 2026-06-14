package me.whereareiam.identica.common.connection.lifecycle;

import me.whereareiam.identica.common.connection.DefaultConnectionLifecycleService;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionProcessAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionResumeAttemptEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Connection Lifecycle Service")
class ConnectionLifecycleServiceTest {
	@DisplayName("Emits completed only once per journey")
	@Test
	void emitsCompletedOnlyOncePerJourney() {
		EventController events = new EventController();
		DefaultConnectionLifecycleService service = service(events, new InMemoryReplicationAdapter());
		Capture capture = new Capture();
		events.register(capture);
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();

		events.call(new ConnectionProcessAttemptEvent(connectionUniqueId, accountUniqueId, "whereareiam", "127.0.0.1"));
		service.completed(connectionUniqueId, accountUniqueId, PipelineType.AUTHENTICATION);
		service.completed(connectionUniqueId, accountUniqueId, PipelineType.AUTHENTICATION);

		assertEquals(1, capture.completed.get());
		assertEquals(0, capture.terminated.get());
		assertEquals(0, capture.disconnected.get());
	}

	@DisplayName("Emits disconnected when the physical connection closes")
	@Test
	void emitsDisconnectedOnPhysicalDisconnect() {
		EventController events = new EventController();
		DefaultConnectionLifecycleService service = service(events, new InMemoryReplicationAdapter());
		Capture capture = new Capture();
		events.register(capture);
		UUID connectionUniqueId = UUID.randomUUID();

		service.disconnected(connectionUniqueId, null, PipelineType.AUTHENTICATION);

		assertEquals(0, capture.completed.get());
		assertEquals(0, capture.terminated.get());
		assertEquals(1, capture.disconnected.get());
	}

	@DisplayName("Emits terminated only once per journey")
	@Test
	void emitsTerminatedOnlyOncePerJourney() {
		EventController events = new EventController();
		DefaultConnectionLifecycleService service = service(events, new InMemoryReplicationAdapter());
		Capture capture = new Capture();
		events.register(capture);
		UUID connectionUniqueId = UUID.randomUUID();

		events.call(new ConnectionProcessAttemptEvent(connectionUniqueId, null, "whereareiam", "127.0.0.1"));
		service.terminated(connectionUniqueId, null, PipelineType.AUTHENTICATION);
		service.terminated(connectionUniqueId, null, PipelineType.AUTHENTICATION);

		assertEquals(0, capture.completed.get());
		assertEquals(1, capture.terminated.get());
		assertEquals(0, capture.disconnected.get());
	}

	@DisplayName("Does not emit terminated after completion")
	@Test
	void doesNotEmitTerminatedAfterCompletion() {
		EventController events = new EventController();
		DefaultConnectionLifecycleService service = service(events, new InMemoryReplicationAdapter());
		Capture capture = new Capture();
		events.register(capture);
		UUID connectionUniqueId = UUID.randomUUID();

		events.call(new ConnectionProcessAttemptEvent(connectionUniqueId, null, "whereareiam", "127.0.0.1"));
		service.completed(connectionUniqueId, null, PipelineType.AUTHENTICATION);
		service.terminated(connectionUniqueId, null, PipelineType.AUTHENTICATION);

		assertEquals(1, capture.completed.get());
		assertEquals(0, capture.terminated.get());
		assertEquals(0, capture.disconnected.get());
	}

	@DisplayName("Resets on resume and forgets finalization state after identity detach")
	@Test
	void resetsOnResumeAndForgetsAfterIdentityDetach() {
		EventController events = new EventController();
		DefaultConnectionLifecycleService service = service(events, new InMemoryReplicationAdapter());
		Capture capture = new Capture();
		events.register(capture);
		UUID connectionUniqueId = UUID.randomUUID();

		events.call(new ConnectionProcessAttemptEvent(connectionUniqueId, null, "whereareiam", "127.0.0.1"));
		service.terminated(connectionUniqueId, null, PipelineType.AUTHENTICATION);
		events.call(new ConnectionResumeAttemptEvent(connectionUniqueId, null, "whereareiam", "127.0.0.1"));
		service.completed(connectionUniqueId, null, PipelineType.AUTHENTICATION);
		events.call(new IdentityDetachedEvent(connectionUniqueId));
		service.terminated(connectionUniqueId, null, PipelineType.AUTHENTICATION);

		assertEquals(1, capture.completed.get());
		assertEquals(1, capture.terminated.get());
		assertEquals(0, capture.disconnected.get());
	}

	@DisplayName("Stale previous instance cannot terminate a reopened journey on another instance")
	@Test
	void stalePreviousInstanceCannotTerminateReopenedJourneyOnAnotherInstance() {
		InMemoryReplicationAdapter adapter = new InMemoryReplicationAdapter();
		EventController eventsA = new EventController();
		EventController eventsB = new EventController();
		DefaultConnectionLifecycleService serviceA = service(eventsA, adapter);
		DefaultConnectionLifecycleService serviceB = service(eventsB, adapter);
		Capture captureA = new Capture();
		Capture captureB = new Capture();
		eventsA.register(captureA);
		eventsB.register(captureB);
		UUID connectionUniqueId = UUID.randomUUID();

		eventsA.call(new ConnectionProcessAttemptEvent(connectionUniqueId, null, "whereareiam", "127.0.0.1"));
		eventsB.call(new ConnectionResumeAttemptEvent(connectionUniqueId, null, "whereareiam", "127.0.0.1"));
		serviceA.terminated(connectionUniqueId, null, PipelineType.AUTHENTICATION);
		serviceB.completed(connectionUniqueId, null, PipelineType.AUTHENTICATION);

		assertEquals(0, captureA.terminated.get());
		assertEquals(1, captureB.completed.get());
		assertEquals(0, captureA.disconnected.get());
		assertEquals(0, captureB.disconnected.get());
	}

	private DefaultConnectionLifecycleService service(
			EventController events,
			InMemoryReplicationAdapter adapter
	) {
		Settings settings = new Settings();
		settings.getSessions().setActiveTtl(Duration.ofMinutes(5));
		return new DefaultConnectionLifecycleService(events, new DefaultReplicationSystem(adapter), () -> settings);
	}

	private static final class Capture implements EventListener {
		private final AtomicInteger completed = new AtomicInteger();
		private final AtomicInteger disconnected = new AtomicInteger();
		private final AtomicInteger terminated = new AtomicInteger();

		@IdenticEvent
		public void onCompleted(ConnectionCompletedEvent event) {
			completed.incrementAndGet();
		}

		@IdenticEvent
		public void onTerminated(ConnectionTerminatedEvent event) {
			terminated.incrementAndGet();
		}

		@IdenticEvent
		public void onDisconnected(ConnectionDisconnectedEvent event) {
			disconnected.incrementAndGet();
		}
	}

	private static final class InMemoryReplicationAdapter implements ReplicationAdapter {
		private final Map<String, byte[]> values = new ConcurrentHashMap<>();

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public @NotNull CompletableFuture<Optional<byte[]>> get(@NotNull String namespace, @NotNull String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.get(namespace + "|" + key)));
		}

		@Override
		public @NotNull CompletableFuture<Optional<byte[]>> consume(@NotNull String namespace, @NotNull String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.remove(namespace + "|" + key)));
		}

		@Override
		public @NotNull CompletableFuture<Void> put(@NotNull String namespace, @NotNull String key, byte[] value, long ttlMs) {
			values.put(namespace + "|" + key, value);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<Void> invalidate(@NotNull String namespace, @NotNull String key) {
			values.remove(namespace + "|" + key);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NotNull CompletableFuture<ReplicationPage> listKeys(@NotNull String namespace, int page, int pageSize) {
			return CompletableFuture.completedFuture(ReplicationPage.empty(Math.max(1, page), Math.max(1, pageSize)));
		}

		@Override
		public @NotNull CompletableFuture<Void> publish(@NotNull String channel, byte[] payload) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void subscribe(@NotNull String channel, @NotNull Consumer<byte[]> handler) {
		}
	}
}
