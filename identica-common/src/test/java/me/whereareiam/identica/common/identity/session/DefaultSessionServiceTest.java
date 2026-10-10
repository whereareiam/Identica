package me.whereareiam.identica.common.identity.session;

import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.ReplicationTestFixtures;
import me.whereareiam.identica.common.replication.event.DefaultReplicatedEventRegistry;
import me.whereareiam.identica.common.replication.event.ReplicatedEventBridge;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.scheduler.*;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Default Session Service")
class DefaultSessionServiceTest {
	@DisplayName("Closing a session publishes the request and emits a session-closed event")
	@Test
	void closePublishesRequestAndEmitsEventWithMessage() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
		EventController eventController = new EventController();
		SessionCloseCapture capture = new SessionCloseCapture();
		replicatedEventBridge("alpha", replicationSystem, eventController);
		eventController.register(capture);

		DefaultSessionService service = sessionService("alpha", replicationSystem, eventController);
		UUID uniqueId = UUID.randomUUID();
		service.open(session(uniqueId)).join();

		service.close(SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnectMessage("closed")
				.build()).join();

		assertEquals(1, adapter.publishCalls);
		assertEquals("identica:events", adapter.lastPublishChannel);
		assertEquals(uniqueId, capture.event.getUniqueId());
		assertEquals("closed", capture.event.getRequest().getDisconnectMessage());
		assertTrue(service.findByUniqueId(uniqueId).join().isEmpty());
	}

	@DisplayName("Remote session-close events are applied without being republished")
	@Test
	void remoteCloseIsAppliedWithoutRepublishing() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
		EventController firstEvents = new EventController();
		EventController secondEvents = new EventController();
		SessionCloseCapture secondCapture = new SessionCloseCapture();
		replicatedEventBridge("alpha", replicationSystem, firstEvents);
		replicatedEventBridge("beta", replicationSystem, secondEvents);
		secondEvents.register(secondCapture);

		DefaultSessionService first = sessionService("alpha", replicationSystem, firstEvents);
		DefaultSessionService second = sessionService("beta", replicationSystem, secondEvents);

		UUID uniqueId = UUID.randomUUID();
		first.open(session(uniqueId)).join();
		second.open(session(uniqueId)).join();

		first.close(SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnect(true)
				.disconnectMessage("remote close")
				.build()).join();
		adapter.emit(adapter.lastPublishPayload);

		assertEquals(1, adapter.publishCalls);
		assertTrue(second.findByUniqueId(uniqueId).join().isEmpty());
		assertEquals("remote close", secondCapture.event.getRequest().getDisconnectMessage());
		assertTrue(secondCapture.event.getRequest().isDisconnect());
	}

	@DisplayName("A node ignores the session-close events that it published itself")
	@Test
	void ownPublishedCloseIsIgnoredWhenReceivedBack() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
		EventController eventController = new EventController();
		SessionCloseCapture capture = new SessionCloseCapture();
		replicatedEventBridge("alpha", replicationSystem, eventController);
		eventController.register(capture);

		DefaultSessionService service = sessionService("alpha", replicationSystem, eventController);
		UUID uniqueId = UUID.randomUUID();
		service.open(session(uniqueId)).join();

		service.close(SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnectMessage("closed")
				.build()).join();
		SessionClosedEvent original = capture.event;
		capture.event = null;

		adapter.emit(adapter.lastPublishPayload);

		assertEquals(1, adapter.publishCalls);
		assertNull(capture.event);
		assertEquals(uniqueId, original.getUniqueId());
	}

	@DisplayName("Sessions use the session-configured default cache TTL")
	@Test
	void sessionsUseSessionConfiguredDefaultCacheTtl() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
		EventController eventController = new EventController();

		DefaultSessionService service = sessionService("alpha", replicationSystem, eventController);

		service.open(session(UUID.randomUUID(), "premium")).join();

		assertEquals(java.time.Duration.ofHours(12).toMillis(), adapter.lastTtlMs);
	}

	@DisplayName("Active sessions schedule a keepalive refresh while the player is online")
	@Test
	void activeSessionsScheduleKeepaliveRefresh() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
		EventController eventController = new EventController();
		TestScheduler scheduler = new TestScheduler();

		DefaultSessionService service = sessionService("alpha", replicationSystem, eventController, providers(), scheduler);
		Session session = session(UUID.randomUUID(), "premium");

		service.open(session).join();
		long firstTtl = adapter.lastTtlMs;

		scheduler.runOnlyPeriodicalTask();

		assertEquals(firstTtl, adapter.lastTtlMs);
		assertTrue(adapter.putCalls >= 2);
	}

	@DisplayName("Provider session settings override the global concurrency setting")
	@Test
	void providerSessionSettingsOverrideGlobalConcurrencySetting() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
		EventController eventController = new EventController();
		UUID uniqueId = UUID.randomUUID();

		DefaultSessionService service = sessionService(
				"alpha",
				replicationSystem,
				eventController,
				providers(provider("premium", SessionConcurrencyPolicy.REJECT_NEW))
		);

		assertEquals(uniqueId, service.open(session(uniqueId, "premium")).join().getUniqueId());
		assertNull(service.open(session(uniqueId, "premium", UUID.randomUUID().toString())).join());
	}

	private DefaultSessionService sessionService(
			String serverId,
			DefaultReplicationSystem replicationSystem,
			EventController eventController
	) {
		return sessionService(serverId, replicationSystem, eventController, providers(), new TestScheduler());
	}

	private DefaultSessionService sessionService(
			String serverId,
			DefaultReplicationSystem replicationSystem,
			EventController eventController,
			Providers providers
	) {
		return sessionService(serverId, replicationSystem, eventController, providers, new TestScheduler());
	}

	private DefaultSessionService sessionService(
			String serverId,
			DefaultReplicationSystem replicationSystem,
			EventController eventController,
			Providers providers,
			Scheduler scheduler
	) {
		return new DefaultSessionService(
				this::settings,
				() -> providers,
				eventController,
				scheduler,
				() -> replication(serverId),
				replicationSystem
		);
	}

	private void replicatedEventBridge(
			String serverId,
			DefaultReplicationSystem replicationSystem,
			EventController eventController
	) {
		new ReplicatedEventBridge(
				eventController,
				replicationSystem,
				() -> replication(serverId),
				new DefaultReplicatedEventRegistry()
		);
	}

	private Session session(UUID uniqueId) {
		return session(uniqueId, "provider");
	}

	private Session session(UUID uniqueId, String providerId) {
		return Session.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.providerSubject(uniqueId.toString())
				.originalUsername("Player")
				.build();
	}

	private Providers providers(Providers.ProviderEntry... entries) {
		Providers providers = new Providers();
		providers.setProviders(List.of(entries));
		return providers;
	}

	private Providers.ProviderEntry provider(String id, SessionConcurrencyPolicy sessionConcurrencyPolicy) {
		Providers.ProviderEntry provider = new Providers.ProviderEntry();
		provider.setId(id);
		provider.setEnabled(true);
		Providers.ProviderEntry.Session session = new Providers.ProviderEntry.Session();
		session.setConcurrencyPolicy(sessionConcurrencyPolicy);
		provider.setSession(session);
		return provider;
	}

	private Settings settings() {
		Settings settings = new Settings();
		Settings.Sessions sessions = new Settings.Sessions();
		sessions.setConcurrencyPolicy(SessionConcurrencyPolicy.REPLACE_EXISTING);
		sessions.setActiveTtl(java.time.Duration.ofHours(12));
		settings.setSessions(sessions);
		return settings;
	}

	private Session session(UUID uniqueId, String providerId, String sessionId) {
		Session session = session(uniqueId, providerId);
		session.setSessionId(sessionId);
		return session;
	}

	private Replication replication(String serverId) {
		Replication replication = new Replication();
		replication.setEnabled(true);
		replication.setServerId(serverId);

		Replication.Cache cache = new Replication.Cache();
		Replication.Sessions sessions = new Replication.Sessions();
		sessions.setUser("sessions:user");
		sessions.setSession("sessions:session");
		sessions.setSubject("sessions:subject");
		cache.setSessions(sessions);
		replication.setCache(cache);

		Replication.Redis redis = new Replication.Redis();
		Replication.Channels channels = new Replication.Channels();
		channels.setSessions("identica:sessions");
		channels.setEvents("identica:events");
		redis.setChannels(channels);
		replication.setRedis(redis);

		return replication;
	}

	private static final class SessionCloseCapture implements EventListener {
		private SessionClosedEvent event;

		@IdenticEvent
		public void onSessionClosed(SessionClosedEvent event) {
			this.event = event;
		}
	}

	private static final class TestScheduler implements Scheduler {
		private final Map<JobKey, PeriodicalRunnableTask> periodicalTasks = new HashMap<>();

		@Override
		public void schedule(RunnableTask runnableTask) {
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask) {
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask) {
			periodicalTasks.put(runnableTask.getKey(), runnableTask);
		}

		@Override
		public void schedule(RunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void cancel(JobKey key) {
			periodicalTasks.remove(key);
		}

		@Override
		public void cancelByOrigin(Origin origin) {
			periodicalTasks.entrySet().removeIf(entry -> entry.getKey().getOrigin().equals(origin));
		}

		private void runOnlyPeriodicalTask() {
			PeriodicalRunnableTask task = periodicalTasks.values().stream().findFirst().orElseThrow();
			task.getRunnable().run();
		}
	}
}
