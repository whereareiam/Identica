package me.whereareiam.identica.common.identity.session;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.replication.LeaseRenewal;
import me.whereareiam.identica.replication.lease.LeaseKeeper;
import me.whereareiam.identica.replication.lease.LeasedRecords;
import me.whereareiam.identica.replication.lease.Leases;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the sessions this proxy holds stored, on a lease of the {@link LeaseKeeper}. A session's record expires
 * after the heartbeat timeout, so the proxy holding its connection writes it again every third of that time, and
 * every tenth time also renews the index entries listing it. A proxy never refreshes a session whose connection
 * another proxy holds.
 * <p>
 * The proxy holding a connection is the authority on its session: a record that expired while the shared
 * store was unreachable is written again from the copy kept here. A session that was closed is released
 * first, so it is not written again.
 */
@Singleton
public class SessionKeepalive {
	/** Record refreshes between two renewals of the index entries, a third of their lifetime. */
	static final int REFRESHES_PER_INDEXING = SessionStore.INDEX_LIFETIMES * LeaseKeeper.RENEWALS_PER_LIFETIME / 3;

	private final Leases<Session> leases;

	@Inject
	public SessionKeepalive(@NotNull SessionStore store, @NotNull LeaseKeeper leaseKeeper) {
		this.leases = leaseKeeper.leases("session", new SessionRecords(store));
	}

	/**
	 * Starts refreshing a session this proxy holds, or takes over its new state when it already does.
	 */
	public void hold(@NotNull Session session) {
		leases.hold(session.getSessionId(), session);
	}

	/**
	 * Stops refreshing a session, which then expires unless it was removed.
	 */
	public void release(@NotNull String sessionId) {
		leases.release(sessionId);
	}

	public boolean holds(@NotNull String sessionId) {
		return leases.holds(sessionId);
	}

	/**
	 * Writes the stored record again, so a change another proxy made to it is kept, or the copy kept here when the
	 * record is gone; the index entries are written with it when the record was gone and on every tenth refresh.
	 * Sessions hold their lease without an end, so each refresh gives the record the store's full lifetime.
	 */
	@RequiredArgsConstructor
	private static final class SessionRecords implements LeasedRecords<Session> {
		private final @NotNull SessionStore store;

		@Override
		public @NotNull CompletableFuture<Optional<Session>> find(@NotNull String sessionId) {
			return store.find(sessionId);
		}

		@Override
		public @NotNull CompletableFuture<Void> write(@NotNull String sessionId, @NotNull Session session, @NotNull LeaseRenewal renewal) {
			return renewal.isRestored() || renewal.getCount() % REFRESHES_PER_INDEXING == 0
					? store.put(session)
					: store.touch(session);
		}
	}
}
