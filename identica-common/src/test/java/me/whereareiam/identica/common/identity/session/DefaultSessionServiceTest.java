package me.whereareiam.identica.common.identity.session;

import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.ReplicationTestFixtures;
import me.whereareiam.identica.common.replication.event.DefaultReplicatedEventRegistry;
import me.whereareiam.identica.common.replication.event.ReplicatedEventBridge;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.event.identity.session.SessionReplacedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.scheduler.*;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("Default Session Service")
class DefaultSessionServiceTest {
	private static final String KICK = "Logged in from another location.";

	private final ReplicationTestFixtures.TestReplicationAdapter adapter = storingAdapter();
	private final DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);

	@DisplayName("Closing a session publishes the request and emits a session-closed event")
	@Test
	void closePublishesRequestAndEmitsEventWithMessage() {
		Node alpha = node("alpha");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(session(uniqueId)).join();
		int published = adapter.publishCalls;

		alpha.service.close(SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnectMessage("closed")
				.build()).join();

		assertEquals(published + 1, adapter.publishCalls);
		assertEquals("identica:events", adapter.lastPublishChannel);
		assertEquals(uniqueId, alpha.closed().getUniqueId());
		assertEquals("closed", alpha.closed().getRequest().getDisconnectMessage());
		assertTrue(alpha.service.findByUniqueId(uniqueId).join().isEmpty());
	}

	@DisplayName("A proxy that receives a close drops its own copy of the session without republishing")
	@Test
	void remoteCloseIsAppliedWithoutRepublishing() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(session(uniqueId)).join();
		assertTrue(beta.service.findByUniqueId(uniqueId).join().isPresent());

		alpha.service.close(SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnect(true)
				.disconnectMessage("remote close")
				.build()).join();
		int published = adapter.publishCalls;
		adapter.emit(adapter.lastPublishPayload);

		assertEquals(published, adapter.publishCalls);
		assertTrue(beta.service.findByUniqueId(uniqueId).join().isEmpty());
		assertEquals("remote close", beta.closed().getRequest().getDisconnectMessage());
		assertTrue(beta.closed().getRequest().isDisconnect());
	}

	@DisplayName("A node ignores the session-close events that it published itself")
	@Test
	void ownPublishedCloseIsIgnoredWhenReceivedBack() {
		Node alpha = node("alpha");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(session(uniqueId)).join();

		alpha.service.close(SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnectMessage("closed")
				.build()).join();
		int closes = alpha.closes.size();

		adapter.emit(adapter.lastPublishPayload);

		assertEquals(closes, alpha.closes.size());
	}

	@DisplayName("Sessions use the session-configured default cache TTL")
	@Test
	void sessionsUseSessionConfiguredDefaultCacheTtl() {
		Node alpha = node("alpha");

		alpha.service.open(session(UUID.randomUUID(), "premium")).join();

		assertEquals(Duration.ofHours(12).toMillis(), adapter.lastTtlMs);
	}

	@DisplayName("Active sessions schedule a keepalive refresh while the player is online")
	@Test
	void activeSessionsScheduleKeepaliveRefresh() {
		Node alpha = node("alpha");
		Session session = connected(UUID.randomUUID(), UUID.randomUUID());

		alpha.service.open(session).join();
		int puts = adapter.putCalls;

		alpha.scheduler.runKeepalive();

		assertEquals(Duration.ofHours(12).toMillis(), adapter.lastTtlMs);
		assertTrue(adapter.putCalls > puts);
	}

	@DisplayName("Provider session settings override the global concurrency setting")
	@Test
	void providerSessionSettingsOverrideGlobalConcurrencySetting() {
		Node alpha = node("alpha", providers(provider("premium", SessionConcurrencyPolicy.REJECT_NEW)));
		UUID uniqueId = UUID.randomUUID();
		Session first = connected(uniqueId, alpha.online());
		first.setProviderId("premium");
		Session second = connected(uniqueId, alpha.online());
		second.setProviderId("premium");

		assertEquals(uniqueId, alpha.service.open(first).join().getUniqueId());
		assertNull(alpha.service.open(second).join());
	}

	@DisplayName("A login from the connection that holds the session continues it under every policy")
	@Test
	void sameConnectionContinuesItsSession() {
		for (SessionConcurrencyPolicy policy : SessionConcurrencyPolicy.values()) {
			Node alpha = node("alpha-" + policy);
			UUID uniqueId = UUID.randomUUID();
			UUID connection = alpha.online();
			Session first = alpha.service.open(connected(uniqueId, connection), policy).join();

			Session again = alpha.service.open(connected(uniqueId, connection), policy).join();

			assertNotNull(again, policy.name());
			assertEquals(first.getSessionId(), again.getSessionId(), policy.name());
			assertTrue(alpha.closes.isEmpty(), policy.name());
		}
	}

	@DisplayName("A login from another proxy replaces the session and disconnects only the earlier connection")
	@Test
	void concurrentLoginReplacesAndTargetsTheEarlierConnection() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		UUID connection = alpha.online();
		Session earlier = alpha.service.open(connected(uniqueId, connection)).join();

		Session later = beta.service.open(connected(uniqueId, connection), SessionConcurrencyPolicy.REPLACE_EXISTING).join();

		assertNotNull(later);
		assertNotEquals(earlier.getSessionId(), later.getSessionId());
		assertEquals("beta", later.getConnection().getServerId());
		assertEquals(earlier.getSessionId(), beta.replaced.getFirst().getExistingSession().getSessionId());
		SessionCloseRequest request = beta.closed().getRequest();
		assertEquals(earlier.getSessionId(), beta.closed().getSession().getSessionId());
		assertTrue(request.isDisconnect());
		assertEquals(KICK, request.getDisconnectMessage());
		assertEquals("alpha", request.getConnection().getServerId());
		assertEquals(connection, request.getConnection().getConnectionUniqueId());
		assertEquals(later.getSessionId(), beta.service.findByUniqueId(uniqueId).join().orElseThrow().getSessionId());
	}

	@DisplayName("The replicated close reaches the earlier proxy and keeps the newer session")
	@Test
	void replicatedReplacementKeepsTheNewerSession() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		UUID connection = alpha.online();
		alpha.service.open(connected(uniqueId, connection)).join();
		Session later = beta.service.open(connected(uniqueId, connection), SessionConcurrencyPolicy.REPLACE_EXISTING).join();

		adapter.emit(adapter.lastPublishPayload);

		SessionClosedEvent received = alpha.closed();
		assertTrue(received.getRequest().isDisconnect());
		assertEquals("alpha", received.getRequest().getConnection().getServerId());
		assertEquals(connection, received.getSession().getConnection().getConnectionUniqueId());
		assertEquals(later.getSessionId(), alpha.service.findByUniqueId(uniqueId).join().orElseThrow().getSessionId());
		assertFalse(alpha.scheduler.hasKeepalive());
	}

	@DisplayName("REJECT_NEW refuses a login from another connection while the earlier one is online")
	@Test
	void rejectNewRefusesWhileTheEarlierConnectionIsOnline() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		Session earlier = alpha.service.open(connected(uniqueId, alpha.online())).join();

		assertNull(beta.service.open(connected(uniqueId, UUID.randomUUID()), SessionConcurrencyPolicy.REJECT_NEW).join());
		assertNull(alpha.service.open(connected(uniqueId, alpha.online()), SessionConcurrencyPolicy.REJECT_NEW).join());

		assertEquals(earlier.getSessionId(), beta.service.findByUniqueId(uniqueId).join().orElseThrow().getSessionId());
		assertTrue(beta.closes.isEmpty());
	}

	@DisplayName("REJECT_NEW accepts a login when the earlier connection is no longer online on its proxy")
	@Test
	void rejectNewAcceptsWhenTheEarlierConnectionIsGone() {
		Node alpha = node("alpha");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(connected(uniqueId, UUID.randomUUID())).join();

		Session later = alpha.service.open(connected(uniqueId, alpha.online()), SessionConcurrencyPolicy.REJECT_NEW).join();

		assertNotNull(later);
		assertFalse(alpha.closed().getRequest().isDisconnect());
	}

	@DisplayName("REJECT_NEW accepts a login when the proxy holding the earlier session stopped announcing itself")
	@Test
	void rejectNewAcceptsWhenTheEarlierProxyStopped() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(connected(uniqueId, alpha.online())).join();
		alpha.presence.onShutdown(new IdenticaShutdownEvent());

		Session later = beta.service.open(connected(uniqueId, UUID.randomUUID()), SessionConcurrencyPolicy.REJECT_NEW).join();

		assertNotNull(later);
		assertFalse(beta.closed().getRequest().isDisconnect());
	}

	@DisplayName("ALLOW_MULTIPLE stores the later session and leaves the earlier connection online")
	@Test
	void allowMultipleKeepsTheEarlierConnection() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(connected(uniqueId, alpha.online())).join();

		Session later = beta.service.open(connected(uniqueId, UUID.randomUUID()), SessionConcurrencyPolicy.ALLOW_MULTIPLE).join();

		assertNotNull(later);
		assertFalse(beta.closed().getRequest().isDisconnect());
		assertTrue(beta.replaced.isEmpty());
		adapter.emit(adapter.lastPublishPayload);
		assertEquals(later.getSessionId(), alpha.service.findByUniqueId(uniqueId).join().orElseThrow().getSessionId());
	}

	@DisplayName("A connection leaving closes its own session")
	@Test
	void leavingClosesTheConnectionsOwnSession() {
		Node alpha = node("alpha");
		UUID uniqueId = UUID.randomUUID();
		UUID connection = alpha.online();
		alpha.service.open(connected(uniqueId, connection)).join();

		alpha.service.close(leaving(uniqueId, connection)).join();

		assertTrue(alpha.service.findByUniqueId(uniqueId).join().isEmpty());
		assertEquals("alpha", alpha.closed().getRequest().getConnection().getServerId());
	}

	@DisplayName("A connection leaving keeps the session another connection of the account holds")
	@Test
	void leavingKeepsTheSessionOfAnotherConnection() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		UUID connection = alpha.online();
		alpha.service.open(connected(uniqueId, connection)).join();
		Session later = beta.service.open(connected(uniqueId, connection), SessionConcurrencyPolicy.ALLOW_MULTIPLE).join();
		int closes = alpha.closes.size();

		alpha.service.close(leaving(uniqueId, connection)).join();

		assertEquals(closes, alpha.closes.size());
		assertEquals(later.getSessionId(), beta.service.findByUniqueId(uniqueId).join().orElseThrow().getSessionId());
	}

	@DisplayName("The keepalive of a superseded session stops instead of refreshing the newer one")
	@Test
	void keepaliveStopsOnceAnotherConnectionHoldsTheSession() {
		Node alpha = node("alpha");
		Node beta = node("beta");
		UUID uniqueId = UUID.randomUUID();
		alpha.service.open(connected(uniqueId, alpha.online())).join();
		beta.service.open(connected(uniqueId, UUID.randomUUID()), SessionConcurrencyPolicy.REPLACE_EXISTING).join();
		assertTrue(alpha.scheduler.hasKeepalive());
		int puts = adapter.putCalls;

		alpha.scheduler.runKeepalive();

		assertEquals(puts, adapter.putCalls);
		assertFalse(alpha.scheduler.hasKeepalive());
	}

	private Node node(String serverId) {
		return node(serverId, providers());
	}

	private Node node(String serverId, Providers providers) {
		return new Node(serverId, providers);
	}

	private final class Node {
		private final EventController events = new EventController();
		private final TestScheduler scheduler = new TestScheduler();
		private final Set<UUID> onlineConnections = new HashSet<>();
		private final List<SessionClosedEvent> closes = new ArrayList<>();
		private final List<SessionReplacedEvent> replaced = new ArrayList<>();
		private final ServerPresence presence;
		private final DefaultSessionService service;

		private Node(String serverId, Providers providers) {
			new ReplicatedEventBridge(events, replicationSystem, () -> replication(serverId), new DefaultReplicatedEventRegistry());
			events.register(new Capture(closes, replaced));
			presence = new ServerPresence(() -> replication(serverId), replicationSystem, scheduler, events);
			service = new DefaultSessionService(
					DefaultSessionServiceTest::settings,
					() -> providers,
					events,
					scheduler,
					() -> replication(serverId),
					DefaultSessionServiceTest::messages,
					replicationSystem,
					identities(),
					presence
			);
		}

		private UUID online() {
			UUID connection = UUID.randomUUID();
			onlineConnections.add(connection);
			return connection;
		}

		private SessionClosedEvent closed() {
			assertFalse(closes.isEmpty(), "no session was closed");
			return closes.getLast();
		}

		private IdentityService identities() {
			IdentityService identities = mock(IdentityService.class, invocation -> {
				if (!invocation.getMethod().getName().equals("findByConnectionUniqueId")) return Optional.empty();
				UUID connection = invocation.getArgument(0);
				return onlineConnections.contains(connection) ? Optional.of(mock(Identity.class)) : Optional.empty();
			});
			return identities;
		}
	}

	private static ReplicationTestFixtures.TestReplicationAdapter storingAdapter() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.storing = true;
		return adapter;
	}

	private static SessionCloseRequest leaving(UUID uniqueId, UUID connection) {
		return SessionCloseRequest.builder()
				.uniqueId(uniqueId)
				.connection(SessionConnection.of(connection))
				.build();
	}

	private static Session session(UUID uniqueId) {
		return session(uniqueId, "provider");
	}

	private static Session session(UUID uniqueId, String providerId) {
		return Session.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.providerSubject(uniqueId.toString())
				.originalUsername("Player")
				.build();
	}

	private static Session connected(UUID uniqueId, UUID connection) {
		Session session = session(uniqueId);
		session.setConnection(SessionConnection.of(connection));
		return session;
	}

	private static Providers providers(Providers.ProviderEntry... entries) {
		Providers providers = new Providers();
		providers.setProviders(List.of(entries));
		return providers;
	}

	private static Providers.ProviderEntry provider(String id, SessionConcurrencyPolicy sessionConcurrencyPolicy) {
		Providers.ProviderEntry provider = new Providers.ProviderEntry();
		provider.setId(id);
		provider.setEnabled(true);
		Providers.ProviderEntry.Session session = new Providers.ProviderEntry.Session();
		session.setConcurrencyPolicy(sessionConcurrencyPolicy);
		provider.setSession(session);
		return provider;
	}

	private static Settings settings() {
		Settings settings = new Settings();
		Settings.Sessions sessions = new Settings.Sessions();
		sessions.setConcurrencyPolicy(SessionConcurrencyPolicy.REPLACE_EXISTING);
		sessions.setActiveTtl(Duration.ofHours(12));
		settings.setSessions(sessions);
		return settings;
	}

	private static Messages messages() {
		Messages messages = new Messages();
		Messages.Engine engine = new Messages.Engine();
		engine.setConcurrentLoginKick(List.of(KICK));
		messages.setEngine(engine);
		return messages;
	}

	private static Replication replication(String serverId) {
		Replication replication = new Replication();
		replication.setEnabled(true);
		replication.setServerId(serverId);

		Replication.Cache cache = new Replication.Cache();
		Replication.Sessions sessions = new Replication.Sessions();
		sessions.setUser("sessions:user");
		sessions.setSession("sessions:session");
		sessions.setSubject("sessions:subject");
		sessions.setServers("sessions:servers");
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

	private record Capture(List<SessionClosedEvent> closes, List<SessionReplacedEvent> replaced) implements EventListener {
		@IdenticEvent
		public void onSessionClosed(SessionClosedEvent event) {
			closes.add(event);
		}

		@IdenticEvent
		public void onSessionReplaced(SessionReplacedEvent event) {
			replaced.add(event);
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

		private boolean hasKeepalive() {
			return periodicalTasks.keySet().stream().anyMatch(key -> key.getOrigin().equals(Origin.core(DefaultSessionService.class)));
		}

		private void runKeepalive() {
			periodicalTasks.entrySet().stream()
					.filter(entry -> entry.getKey().getOrigin().equals(Origin.core(DefaultSessionService.class)))
					.map(Map.Entry::getValue)
					.findFirst()
					.orElseThrow()
					.getRunnable()
					.run();
		}
	}
}
