package me.whereareiam.identica.common.identity.session;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.service.Scheduler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the sessions this proxy holds stored. A session's record expires after the heartbeat timeout, so
 * the proxy holding its connection writes it again every third of that time, and every tenth time also
 * renews the index entries listing it. A proxy never refreshes a session whose connection another proxy
 * holds.
 * <p>
 * The proxy holding a connection is the authority on its session: a record that expired while the shared
 * store was unreachable is written again from the copy kept here. A session that was closed is released
 * first, so it is not written again.
 */
@Singleton
public class SessionKeepalive implements EventListener {
	/** Refreshes within one record lifetime. */
	static final int REFRESHES_PER_LIFETIME = 3;
	/** Record refreshes between two renewals of the index entries, a third of their lifetime. */
	static final int REFRESHES_PER_INDEXING = SessionStore.INDEX_LIFETIMES * REFRESHES_PER_LIFETIME / 3;

	private static final Origin ORIGIN = Origin.core(SessionKeepalive.class);
	private static final JobKey JOB_KEY = JobKey.of(ORIGIN, Purpose.of("session-keepalive"), "refresh");

	private final SessionStore store;
	private final Scheduler scheduler;
	private final Map<String, Held> held = new ConcurrentHashMap<>();

	@Inject
	public SessionKeepalive(SessionStore store, Scheduler scheduler, EventManager eventManager) {
		this.store = store;
		this.scheduler = scheduler;

		long interval = store.lifetimeMs() / REFRESHES_PER_LIFETIME;
		scheduler.schedule(PeriodicalRunnableTask.builder()
				.key(JOB_KEY)
				.delay(interval)
				.period(interval)
				.runnable(this::refresh)
				.build());
		eventManager.register(this);
	}

	/**
	 * Starts refreshing a session this proxy holds, or takes over its new state when it already does.
	 */
	public void hold(@NotNull Session session) {
		held.compute(session.getSessionId(), (ignored, current) -> new Held(session, current != null ? current.refreshes : 0));
	}

	/**
	 * Stops refreshing a session, which then expires unless it was removed.
	 */
	public void release(@NotNull String sessionId) {
		held.remove(sessionId);
	}

	public boolean holds(@NotNull String sessionId) {
		return held.containsKey(sessionId);
	}

	@IdenticEvent
	public void onShutdown(@NotNull IdenticaShutdownEvent event) {
		scheduler.cancel(JOB_KEY);
		held.clear();
	}

	private void refresh() {
		for (String sessionId : held.keySet())
			refresh(sessionId);
	}

	/**
	 * Writes the stored record again, so a change another proxy made to it is kept, or the copy kept here
	 * when the record is gone.
	 */
	private void refresh(@NotNull String sessionId) {
		store.find(sessionId)
				.thenCompose(stored -> {
					Held refreshed = held.computeIfPresent(sessionId, (ignored, current) ->
							new Held(stored.orElse(current.session), current.refreshes + 1));
					if (refreshed == null) return CompletableFuture.<Void>completedFuture(null);

					return stored.isEmpty() || refreshed.refreshes % REFRESHES_PER_INDEXING == 0
							? store.put(refreshed.session)
							: store.touch(refreshed.session);
				})
				.exceptionally(failure -> {
					Logger.debug("Could not refresh session %s: %s", sessionId, failure.getMessage());
					return null;
				});
	}

	private record Held(@NotNull Session session, int refreshes) {
	}
}
