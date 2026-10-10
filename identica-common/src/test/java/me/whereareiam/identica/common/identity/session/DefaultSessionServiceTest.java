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
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static me.whereareiam.identica.common.identity.session.SessionFixtures.LIFETIME_MS;
import static me.whereareiam.identica.common.identity.session.SessionFixtures.replication;
import static me.whereareiam.identica.common.identity.session.SessionFixtures.session;
import static me.whereareiam.identica.type.session.SessionConcurrencyPolicy.ALLOW_MULTIPLE;
import static me.whereareiam.identica.type.session.SessionConcurrencyPolicy.REJECT_NEW;
import static me.whereareiam.identica.type.session.SessionConcurrencyPolicy.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Two or three proxies on one shared store, each with its own session service, keepalive and events.
 */
@DisplayName("Default Session Service")
class DefaultSessionServiceTest {
	private static final String KICK = "Logged in from another location.";

	private final ReplicationTestFixtures.TestReplicationAdapter adapter = SessionFixtures.sharedStore();
	private final Node alpha = new Node("alpha", providers());
	private final Node beta = new Node("beta", providers());
	private final UUID account = UUID.randomUUID();

	@DisplayName("A login from the connection that holds a session continues it under every policy")
	@Test
	void sameConnectionContinuesItsSession() {
		for (SessionConcurrencyPolicy policy : SessionConcurrencyPolicy.values()) {
			UUID uniqueId = UUID.randomUUID();
			UUID connection = alpha.online();
			Session first = alpha.service.open(connected(uniqueId, connection), policy).join();

			Session again = alpha.service.open(connected(uniqueId, connection), policy).join();

			assertNotNull(again, policy.name());
			assertEquals(first.getSessionId(), again.getSessionId(), policy.name());
			assertEquals(1, beta.service.findAllByUniqueId(uniqueId).join().size(), policy.name());
		}
		assertTrue(alpha.closes.isEmpty());
	}

	@DisplayName("REPLACE_EXISTING closes the earlier session and disconnects only its connection")
	@Test
	void replaceClosesAndTargetsTheEarlierConnection() {
		UUID connection = alpha.online();
		Session earlier = alpha.service.open(connected(account, connection)).join();

		Session later = beta.service.open(connected(account, connection), REPLACE_EXISTING).join();

		assertNotEquals(earlier.getSessionId(), later.getSessionId());
		assertEquals(earlier.getSessionId(), beta.replaced.getFirst().getExistingSession().getSessionId());
		SessionClosedEvent received = alpha.closed();
		assertEquals(earlier.getSessionId(), received.getSession().getSessionId());
		assertTrue(received.getRequest().isDisconnect());
		assertEquals(KICK, received.getRequest().getDisconnectMessage());
		assertEquals("alpha", received.getRequest().getConnection().getServerId());
		assertEquals(connection, received.getRequest().getConnection().getConnectionUniqueId());
		assertEquals(List.of(later.getSessionId()), ids(alpha.service.findAllByUniqueId(account).join()));
		assertFalse(alpha.keepalive.holds(earlier.getSessionId()));
		assertTrue(beta.keepalive.holds(later.getSessionId()));
	}

	@DisplayName("REPLACE_EXISTING replaces the sessions of every other connection")
	@Test
	void replaceClosesEveryOtherConnection() {
		Node gamma = new Node("gamma", providers());
		alpha.service.open(connected(account, alpha.online()), ALLOW_MULTIPLE).join();
		beta.service.open(connected(account, beta.online()), ALLOW_MULTIPLE).join();

		Session latest = gamma.service.open(connected(account, gamma.online()), REPLACE_EXISTING).join();

		assertEquals(List.of(latest.getSessionId()), ids(alpha.service.findAllByUniqueId(account).join()));
		assertEquals(List.of("alpha", "beta"), alpha.closes.stream()
				.map(close -> close.getRequest().getConnection().getServerId())
				.toList());
		assertEquals(2, gamma.closes.stream().filter(close -> close.getRequest().isDisconnect()).count());
	}

	@DisplayName("REJECT_NEW refuses a login from another connection while a session exists")
	@Test
	void rejectNewRefusesWhileAnotherSessionExists() {
		Session earlier = alpha.service.open(connected(account, alpha.online())).join();

		assertNull(beta.service.open(connected(account, beta.online()), REJECT_NEW).join());
		assertNull(alpha.service.open(connected(account, alpha.online()), REJECT_NEW).join());

		assertEquals(List.of(earlier.getSessionId()), ids(beta.service.findAllByUniqueId(account).join()));
		assertTrue(beta.closes.isEmpty());
	}

	@DisplayName("REJECT_NEW accepts a login when the earlier connection is no longer online on this proxy")
	@Test
	void rejectNewAcceptsWhenTheEarlierConnectionIsGoneHere() {
		Session earlier = alpha.service.open(connected(account, UUID.randomUUID())).join();

		Session later = alpha.service.open(connected(account, alpha.online()), REJECT_NEW).join();

		assertNotNull(later);
		assertEquals(earlier.getSessionId(), alpha.closed().getSession().getSessionId());
		assertFalse(alpha.closed().getRequest().isDisconnect());
		assertEquals(List.of(later.getSessionId()), ids(beta.service.findAllByUniqueId(account).join()));
	}

	@DisplayName("REJECT_NEW accepts a login once the session of a proxy that stopped has expired")
	@Test
	void rejectNewAcceptsOnceTheStoppedProxysSessionExpired() {
		Session earlier = alpha.service.open(connected(account, alpha.online())).join();
		assertNull(beta.service.open(connected(account, beta.online()), REJECT_NEW).join());

		// alpha stopped: nothing refreshes the record, and the store drops it after the heartbeat timeout.
		adapter.drop("sessions:records", earlier.getSessionId());

		assertNotNull(beta.service.open(connected(account, beta.online()), REJECT_NEW).join());
	}

	@DisplayName("ALLOW_MULTIPLE keeps the session of every connection")
	@Test
	void allowMultipleKeepsEverySession() {
		Session earlier = alpha.service.open(connected(account, alpha.online())).join();

		Session later = beta.service.open(connected(account, beta.online()), ALLOW_MULTIPLE).join();

		assertEquals(List.of(earlier.getSessionId(), later.getSessionId()), ids(alpha.service.findAllByUniqueId(account).join()));
		assertEquals(later.getSessionId(), alpha.service.findByUniqueId(account).join().orElseThrow().getSessionId());
		assertTrue(alpha.closes.isEmpty());
		assertTrue(beta.replaced.isEmpty());
		assertTrue(alpha.keepalive.holds(earlier.getSessionId()));
	}

	@DisplayName("A connection finds its own session among the account's")
	@Test
	void findsTheSessionOfAConnection() {
		UUID first = alpha.online();
		UUID second = beta.online();
		Session earlier = alpha.service.open(connected(account, first)).join();
		Session later = beta.service.open(connected(account, second), ALLOW_MULTIPLE).join();

		assertEquals(earlier.getSessionId(), alpha.service.findByConnection(account, SessionConnection.of(first)).join().orElseThrow().getSessionId());
		assertEquals(later.getSessionId(), beta.service.findByConnection(account, SessionConnection.of(second)).join().orElseThrow().getSessionId());
		assertEquals(later.getSessionId(), alpha.service.findByConnection(account, new SessionConnection("beta", second)).join().orElseThrow().getSessionId());
		assertTrue(alpha.service.findByConnection(account, SessionConnection.of(second)).join().isEmpty());
	}

	@DisplayName("A connection leaving closes its own session and keeps the other connection's")
	@Test
	void leavingClosesOnlyTheConnectionsOwnSession() {
		UUID connection = alpha.online();
		Session earlier = alpha.service.open(connected(account, connection)).join();
		Session later = beta.service.open(connected(account, connection), ALLOW_MULTIPLE).join();

		alpha.service.close(leaving(account, connection)).join();

		assertEquals(List.of(later.getSessionId()), ids(beta.service.findAllByUniqueId(account).join()));
		assertEquals(1, beta.closes.size());
		assertEquals(earlier.getSessionId(), beta.closed().getSession().getSessionId());
		assertFalse(beta.closed().getRequest().isDisconnect());
		assertFalse(alpha.keepalive.holds(earlier.getSessionId()));
		assertTrue(beta.keepalive.holds(later.getSessionId()));
	}

	@DisplayName("A connection without a session closes nothing when it leaves")
	@Test
	void leavingWithoutASessionClosesNothing() {
		Session other = beta.service.open(connected(account, beta.online())).join();

		alpha.service.close(leaving(account, UUID.randomUUID())).join();

		assertEquals(List.of(other.getSessionId()), ids(alpha.service.findAllByUniqueId(account).join()));
		assertTrue(alpha.closes.isEmpty());
	}

	@DisplayName("Closing an account closes every session and disconnects each connection where it is held")
	@Test
	void closingAnAccountClosesEverySession() {
		UUID first = alpha.online();
		UUID second = beta.online();
		alpha.service.open(connected(account, first)).join();
		beta.service.open(connected(account, second), ALLOW_MULTIPLE).join();

		beta.service.close(SessionCloseRequest.builder()
				.uniqueId(account)
				.disconnect(true)
				.disconnectMessage("ended")
				.build()).join();

		assertTrue(alpha.service.findAllByUniqueId(account).join().isEmpty());
		assertEquals(2, alpha.closes.size());
		assertEquals(Set.of(first, second), connections(alpha.closes));
		assertTrue(alpha.closes.stream().allMatch(close -> close.getRequest().isDisconnect()
				&& "ended".equals(close.getRequest().getDisconnectMessage())));
		assertEquals(2, alpha.closes.stream().map(SessionClosedEvent::getReplicationEventId).distinct().count());
	}

	@DisplayName("Closing an account without a session still announces the close to every proxy")
	@Test
	void closingAnAccountWithoutASessionAnnouncesIt() {
		alpha.service.close(SessionCloseRequest.builder()
				.uniqueId(account)
				.disconnect(true)
				.disconnectMessage("ended")
				.build()).join();

		assertNull(beta.closed().getSession());
		assertNull(beta.closed().getRequest().getConnection());
		assertTrue(beta.closed().getRequest().isDisconnect());
	}

	@DisplayName("The proxy holding a closed session removes a record its keepalive wrote during the close")
	@Test
	void holderRemovesARecordWrittenDuringTheClose() {
		Session held = alpha.service.open(connected(account, alpha.online())).join();
		beta.afterClose = () -> alpha.store.put(held).join();

		beta.service.close(account).join();

		assertTrue(beta.service.findBySessionId(held.getSessionId()).join().isEmpty());
		assertFalse(alpha.keepalive.holds(held.getSessionId()));
	}

	@DisplayName("Reopening a stored session with a change updates that session in place")
	@Test
	void reopeningAStoredSessionUpdatesIt() {
		Session held = alpha.service.open(connected(account, alpha.online())).join();
		Session read = beta.service.findByUniqueId(account).join().orElseThrow();
		read.setEffectiveUsername("Renamed");

		beta.service.open(read, REJECT_NEW).join();

		assertEquals(List.of(held.getSessionId()), ids(alpha.service.findAllByUniqueId(account).join()));
		assertEquals("Renamed", alpha.service.findBySessionId(held.getSessionId()).join().orElseThrow().getEffectiveUsername());
		assertFalse(beta.keepalive.holds(held.getSessionId()));
	}

	@DisplayName("Sessions are listed by their ids")
	@Test
	void listsSessionIds() {
		Session first = alpha.service.open(connected(account, alpha.online())).join();
		Session second = beta.service.open(connected(account, beta.online()), ALLOW_MULTIPLE).join();

		assertEquals(Set.of(first.getSessionId(), second.getSessionId()), Set.copyOf(alpha.service.list(1, 10).join().entries()));
		assertEquals(2, alpha.service.list(1, 10).join().total());
	}

	@DisplayName("Provider session settings override the global concurrency setting")
	@Test
	void providerSessionSettingsOverrideGlobalConcurrencySetting() {
		Node gamma = new Node("gamma", providers(provider("premium", REJECT_NEW)));
		Session first = connected(account, gamma.online());
		first.setProviderId("premium");
		Session second = connected(account, gamma.online());
		second.setProviderId("premium");

		assertNotNull(gamma.service.open(first).join());
		assertNull(gamma.service.open(second).join());
	}

	private final class Node {
		private final EventController events = new EventController();
		private final Set<UUID> onlineConnections = new HashSet<>();
		private final List<SessionClosedEvent> closes = new ArrayList<>();
		private final List<SessionReplacedEvent> replaced = new ArrayList<>();
		private final SessionStore store;
		private final SessionKeepalive keepalive;
		private final DefaultSessionService service;
		/** Runs on this proxy after it announced a close and before the other proxies hear of it. */
		private Runnable afterClose = () -> {};

		private Node(String serverId, Providers providers) {
			DefaultReplicationSystem replicationSystem = new DefaultReplicationSystem(adapter);
			events.register(new Capture(this));
			new ReplicatedEventBridge(events, replicationSystem, () -> replication(serverId), new DefaultReplicatedEventRegistry());
			store = new SessionStore(LIFETIME_MS, replication(serverId), replicationSystem);
			keepalive = new SessionKeepalive(store, new SessionFixtures.ManualScheduler(), events);
			service = new DefaultSessionService(
					DefaultSessionServiceTest::settings,
					() -> providers,
					() -> replication(serverId),
					DefaultSessionServiceTest::messages,
					identities(),
					events,
					store,
					keepalive
			);
		}

		/** A connection that is online on this proxy. */
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
			return mock(IdentityService.class, invocation -> {
				if (!invocation.getMethod().getName().equals("findByConnectionUniqueId")) return Optional.empty();
				UUID connection = invocation.getArgument(0);
				return onlineConnections.contains(connection) ? Optional.of(mock(Identity.class)) : Optional.empty();
			});
		}
	}

	public static final class Capture implements EventListener {
		private final Node node;

		private Capture(Node node) {
			this.node = node;
		}

		@IdenticEvent
		public void onSessionClosed(SessionClosedEvent event) {
			node.closes.add(event);
			node.afterClose.run();
		}

		@IdenticEvent
		public void onSessionReplaced(SessionReplacedEvent event) {
			node.replaced.add(event);
		}
	}

	private static SessionCloseRequest leaving(UUID uniqueId, UUID connection) {
		return SessionCloseRequest.builder()
				.uniqueId(uniqueId)
				.connection(SessionConnection.of(connection))
				.build();
	}

	private static Session connected(UUID uniqueId, UUID connection) {
		Session session = session(uniqueId);
		session.setConnection(SessionConnection.of(connection));
		return session;
	}

	private static List<String> ids(List<Session> sessions) {
		return sessions.stream().map(Session::getSessionId).toList();
	}

	private static Set<UUID> connections(List<SessionClosedEvent> closes) {
		Set<UUID> connections = new HashSet<>();
		for (SessionClosedEvent close : closes)
			connections.add(close.getRequest().getConnection().getConnectionUniqueId());
		return connections;
	}

	private static Providers providers(Providers.ProviderEntry... entries) {
		Providers providers = new Providers();
		providers.setProviders(List.of(entries));
		return providers;
	}

	private static Providers.ProviderEntry provider(String id, SessionConcurrencyPolicy policy) {
		Providers.ProviderEntry provider = new Providers.ProviderEntry();
		provider.setId(id);
		provider.setEnabled(true);
		Providers.ProviderEntry.Session session = new Providers.ProviderEntry.Session();
		session.setConcurrencyPolicy(policy);
		provider.setSession(session);
		return provider;
	}

	private static Settings settings() {
		Settings settings = new Settings();
		Settings.Sessions sessions = new Settings.Sessions();
		sessions.setConcurrencyPolicy(REPLACE_EXISTING);
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
}
