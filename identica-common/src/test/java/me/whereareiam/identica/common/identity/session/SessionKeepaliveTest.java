package me.whereareiam.identica.common.identity.session;

import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.ReplicationTestFixtures;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.model.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.whereareiam.identica.common.identity.session.SessionFixtures.LIFETIME_MS;
import static me.whereareiam.identica.common.identity.session.SessionFixtures.replication;
import static me.whereareiam.identica.common.identity.session.SessionFixtures.stored;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Session Keepalive")
class SessionKeepaliveTest {
	private static final String RECORDS = "sessions:records";
	private static final String ACCOUNTS = "sessions:accounts";

	private final ReplicationTestFixtures.TestReplicationAdapter adapter = SessionFixtures.sharedStore();
	private final SessionStore store = new SessionStore(LIFETIME_MS, replication("alpha"), new DefaultReplicationSystem(adapter));
	private final SessionFixtures.ManualScheduler scheduler = new SessionFixtures.ManualScheduler();
	private final SessionKeepalive keepalive = new SessionKeepalive(store, scheduler, new EventController());
	private final UUID account = UUID.randomUUID();

	@DisplayName("Held sessions are refreshed every third of the heartbeat timeout")
	@Test
	void refreshesEveryThirdOfTheLifetime() {
		assertEquals(LIFETIME_MS / 3, scheduler.period());
	}

	@DisplayName("A refresh reads and writes the record of each held session and nothing else")
	@Test
	void refreshCostsOneReadAndOneWritePerHeldSession() {
		Session held = held();
		Session notHeld = stored(UUID.randomUUID(), "beta", 1);
		store.put(notHeld).join();
		int gets = adapter.getCalls;
		int puts = adapter.putCalls;

		scheduler.tick();

		assertEquals(gets + 1, adapter.getCalls);
		assertEquals(puts + 1, adapter.putCalls);
		assertEquals(RECORDS, adapter.lastNamespace);
		assertEquals(held.getSessionId(), adapter.lastKey);
	}

	@DisplayName("Every tenth refresh also renews the index entries of the session")
	@Test
	void renewsTheIndexEntriesEveryTenthRefresh() {
		held();
		adapter.drop(ACCOUNTS, account.toString());

		for (int refresh = 1; refresh < SessionKeepalive.REFRESHES_PER_INDEXING; refresh++)
			scheduler.tick();
		assertNull(adapter.read(ACCOUNTS, account.toString()));

		scheduler.tick();

		assertNotNull(adapter.read(ACCOUNTS, account.toString()));
	}

	@DisplayName("A change another proxy made to the record survives the refresh")
	@Test
	void refreshKeepsWhatAnotherProxyChanged() {
		Session held = held();
		SessionStore other = new SessionStore(LIFETIME_MS, replication("beta"), new DefaultReplicationSystem(adapter));
		other.put(held.toBuilder().effectiveUsername("Renamed").build()).join();

		scheduler.tick();

		assertEquals("Renamed", store.find(held.getSessionId()).join().orElseThrow().getEffectiveUsername());
	}

	@DisplayName("A record that expired while the store was unreachable is written again with its index entries")
	@Test
	void restoresAnExpiredRecordOfAHeldSession() {
		Session held = held();
		adapter.drop(RECORDS, held.getSessionId());
		adapter.drop(ACCOUNTS, account.toString());

		scheduler.tick();

		assertTrue(store.find(held.getSessionId()).join().isPresent());
		assertEquals(1, store.findByAccount(account).join().size());
	}

	@DisplayName("A released session is no longer refreshed or written again")
	@Test
	void releasedSessionIsLeftAlone() {
		Session held = held();
		keepalive.release(held.getSessionId());
		store.remove(held).join();
		int puts = adapter.putCalls;

		scheduler.tick();

		assertEquals(puts, adapter.putCalls);
		assertFalse(keepalive.holds(held.getSessionId()));
		assertTrue(store.find(held.getSessionId()).join().isEmpty());
	}

	@DisplayName("Shutting down stops the refresh")
	@Test
	void shutdownStopsRefreshing() {
		held();

		keepalive.onShutdown(new IdenticaShutdownEvent());

		assertFalse(scheduler.scheduled());
	}

	private Session held() {
		Session session = stored(account, "alpha", 1);
		store.put(session).join();
		keepalive.hold(session);
		return session;
	}
}
