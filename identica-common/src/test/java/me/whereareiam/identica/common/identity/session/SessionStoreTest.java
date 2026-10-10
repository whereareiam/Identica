package me.whereareiam.identica.common.identity.session;

import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.ReplicationTestFixtures;
import me.whereareiam.identica.model.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static me.whereareiam.identica.common.identity.session.SessionFixtures.LIFETIME_MS;
import static me.whereareiam.identica.common.identity.session.SessionFixtures.replication;
import static me.whereareiam.identica.common.identity.session.SessionFixtures.stored;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Session Store")
class SessionStoreTest {
	private final ReplicationTestFixtures.TestReplicationAdapter adapter = SessionFixtures.sharedStore();
	private final SessionStore alpha = store("alpha", LIFETIME_MS);
	private final SessionStore beta = store("beta", LIFETIME_MS);
	private final UUID account = UUID.randomUUID();

	@DisplayName("A session is one record, found by its id, its account and its provider subject on every proxy")
	@Test
	void storesOneRecordAndFindsItEveryWay() {
		Session session = stored(account, "alpha", 1);
		alpha.put(session).join();

		assertEquals(session.getSessionId(), beta.find(session.getSessionId()).join().orElseThrow().getSessionId());
		assertEquals(List.of(session.getSessionId()), ids(beta.findByAccount(account).join()));
		assertEquals(List.of(session.getSessionId()), ids(beta.findBySubject("Provider", account.toString().toUpperCase()).join()));
		assertEquals(List.of(session.getSessionId()), beta.list(1, 10).join().getEntries());
		assertEquals(LIFETIME_MS, adapter.ttlOf("sessions:records", session.getSessionId()));
	}

	@DisplayName("An account keeps a session per connection, oldest first")
	@Test
	void keepsSeveralSessionsOfAnAccount() {
		Session later = stored(account, "beta", 2);
		Session earlier = stored(account, "alpha", 1);
		beta.put(later).join();
		alpha.put(earlier).join();

		assertEquals(List.of(earlier.getSessionId(), later.getSessionId()), ids(alpha.findByAccount(account).join()));
	}

	@DisplayName("Removing a session leaves the account's other session and what another proxy reads up to date")
	@Test
	void removesOnlyTheSessionItIsGiven() {
		Session first = stored(account, "alpha", 1);
		Session second = stored(account, "beta", 2);
		alpha.put(first).join();
		beta.put(second).join();
		assertEquals(2, beta.findByAccount(account).join().size());

		alpha.remove(first).join();

		assertTrue(beta.find(first.getSessionId()).join().isEmpty());
		assertEquals(List.of(second.getSessionId()), ids(beta.findByAccount(account).join()));
		assertEquals(second.getSessionId(), adapter.read("sessions:accounts", account.toString()));
	}

	@DisplayName("An index entry naming a session whose record is gone is ignored and cleaned up")
	@Test
	void ignoresAndCleansStaleIndexEntries() {
		Session live = stored(account, "alpha", 1);
		Session gone = stored(account, "beta", 2);
		alpha.put(live).join();
		beta.put(gone).join();
		adapter.drop("sessions:records", gone.getSessionId());

		assertEquals(List.of(live.getSessionId()), ids(alpha.findByAccount(account).join()));

		assertEquals(live.getSessionId(), adapter.read("sessions:accounts", account.toString()));
	}

	@DisplayName("An index entry whose last session is gone is removed")
	@Test
	void removesAnIndexEntryWithoutSessions() {
		Session gone = stored(account, "alpha", 1);
		alpha.put(gone).join();
		adapter.drop("sessions:records", gone.getSessionId());

		assertTrue(beta.findByAccount(account).join().isEmpty());

		assertNull(adapter.read("sessions:accounts", account.toString()));
	}

	@DisplayName("A session listed under a key it does not belong to is not returned for it")
	@Test
	void validatesIndexedSessionsAgainstTheirRecord() {
		Session session = stored(account, "alpha", 1);
		alpha.put(session).join();
		UUID other = UUID.randomUUID();
		Session misplaced = session.toBuilder().uniqueId(other).build();
		alpha.index(misplaced).join();
		assertEquals(session.getSessionId(), adapter.read("sessions:accounts", other.toString()));

		assertTrue(alpha.findByAccount(other).join().isEmpty());
	}

	@DisplayName("Indexing a session again restores an index entry that lost it")
	@Test
	void indexingRestoresALostEntry() {
		Session session = stored(account, "alpha", 1);
		alpha.put(session).join();
		adapter.drop("sessions:accounts", account.toString());
		assertTrue(beta.findByAccount(account).join().isEmpty());

		alpha.index(session).join();

		assertEquals(List.of(session.getSessionId()), ids(beta.findByAccount(account).join()));
		assertEquals(LIFETIME_MS * SessionStore.INDEX_LIFETIMES, adapter.ttlOf("sessions:accounts", account.toString()));
	}

	@DisplayName("A record that is not refreshed expires, and with it the session on every proxy")
	@Test
	void recordExpiresWithoutRefresh() throws InterruptedException {
		SessionStore shortLived = store("alpha", 60);
		Session session = stored(account, "alpha", 1);
		shortLived.put(session).join();
		assertTrue(beta.find(session.getSessionId()).join().isPresent());

		Thread.sleep(120);

		assertTrue(beta.find(session.getSessionId()).join().isEmpty());
		assertTrue(shortLived.findByAccount(account).join().isEmpty());
	}

	@DisplayName("Touching a record gives it its lifetime again without writing the index entries")
	@Test
	void touchingRewritesOnlyTheRecord() {
		Session session = stored(account, "alpha", 1);
		alpha.put(session).join();
		int puts = adapter.putCalls;

		alpha.touch(session).join();

		assertEquals(puts + 1, adapter.putCalls);
		assertEquals("sessions:records", adapter.lastNamespace);
	}

	private SessionStore store(String serverId, long lifetimeMs) {
		return new SessionStore(lifetimeMs, replication(serverId), new DefaultReplicationSystem(adapter));
	}

	private static List<String> ids(List<Session> sessions) {
		return sessions.stream().map(Session::getSessionId).toList();
	}
}
