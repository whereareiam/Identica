package me.whereareiam.identica.common.identity.session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.service.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Announces that this proxy is running, so another proxy can tell a session held by a running proxy from one
 * left behind by a proxy that stopped without closing its sessions. Each proxy refreshes a short-lived entry
 * under its replication server id; the entry expires soon after the proxy stops refreshing it.
 */
@Singleton
public class ServerPresence implements EventListener {
	static final long TTL_MS = 30_000L;
	static final long REFRESH_INTERVAL_MS = 10_000L;

	private static final Origin ORIGIN = Origin.core(ServerPresence.class);
	private static final JobKey JOB_KEY = JobKey.of(ORIGIN, Purpose.of("server-presence"), "announce");

	private final Provider<Replication> replicationProvider;
	private final Scheduler scheduler;
	private final ReplicatedCache<String> servers;

	@Inject
	public ServerPresence(
			Provider<Replication> replicationProvider,
			ReplicationSystem replicationSystem,
			Scheduler scheduler,
			EventManager eventManager
	) {
		this.replicationProvider = replicationProvider;
		this.scheduler = scheduler;
		this.servers = replicationSystem.cache(resolveNamespace(replicationProvider))
				.defaultTtl(TTL_MS)
				.replicated(ReplicationType.identity(String.class).withCodec(SnapshotCodec.string()));

		announce();
		scheduler.schedule(PeriodicalRunnableTask.builder()
				.key(JOB_KEY)
				.delay(REFRESH_INTERVAL_MS)
				.period(REFRESH_INTERVAL_MS)
				.runnable(this::announce)
				.build());
		eventManager.register(this);
	}

	/**
	 * Returns whether a proxy announced itself within the last {@link #TTL_MS} milliseconds.
	 *
	 * @param serverId replication server id of the proxy
	 * @return {@code true} while the proxy is running
	 */
	public boolean isRunning(@Nullable String serverId) {
		if (serverId == null || serverId.isBlank()) return false;

		try {
			return servers.getFresh(serverId.trim()).join().isPresent();
		} catch (RuntimeException failure) {
			Logger.debug("Could not look up whether server %s is running, assuming it is: %s", serverId, failure.getMessage());
			return true;
		}
	}

	@IdenticEvent
	public void onShutdown(@NotNull IdenticaShutdownEvent event) {
		scheduler.cancel(JOB_KEY);
		try {
			servers.invalidate(serverId()).join();
		} catch (RuntimeException failure) {
			Logger.debug("Could not withdraw the presence of this server: %s", failure.getMessage());
		}
	}

	private void announce() {
		String serverId = serverId();
		try {
			servers.put(serverId, serverId).join();
		} catch (RuntimeException failure) {
			Logger.debug("Could not announce the presence of this server: %s", failure.getMessage());
		}
	}

	private @NotNull String serverId() {
		return replicationProvider.get().getServerId().trim();
	}

	private static String resolveNamespace(Provider<Replication> replicationProvider) {
		String namespace = replicationProvider.get().getCache().getSessions().getServers();
		if (namespace == null || namespace.isBlank())
			throw new IllegalStateException("replication.cache.sessions.servers is missing");

		return namespace;
	}
}
