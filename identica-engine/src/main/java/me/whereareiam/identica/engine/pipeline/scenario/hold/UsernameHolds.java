package me.whereareiam.identica.engine.pipeline.scenario.hold;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.scenario.ScenarioRequiredEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, for every username, the connection that is logging in under it, so that a proxy of a network refuses
 * another connection with that username while the first one logs in. A connection holds its username from the moment
 * its login waits for the player until the login ends, the player leaves or the login times out.
 *
 * <p>A proxy refuses a second connection of a username that one of its own players uses by itself; the holds matter
 * between the proxies of a replicated network.</p>
 */
@Singleton
public class UsernameHolds implements EventListener {
	private static final String NAMESPACE_SUFFIX = ":username-holds";

	private final Provider<Replication> replicationProvider;
	private final Supplier<ReplicatedCache<Holder>> holders;
	private final Map<UUID, String> heldHere = new ConcurrentHashMap<>();

	@Inject
	public UsernameHolds(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull EventManager eventManager
	) {
		this.replicationProvider = replicationProvider;
		this.holders = Suppliers.memoize(() -> replicationSystem
				.cache(replicationProvider.get().getCache().getPipelineState() + NAMESPACE_SUFFIX)
				.replicated(ReplicationType.identity(Holder.class)));
		eventManager.register(this);
	}

	/**
	 * Finds the connection whose login in progress holds the username of an arriving connection from another
	 * address. A connection from the same address is the same player returning, who continues the login in
	 * progress.
	 *
	 * @param connectionUniqueId unique id of the arriving connection
	 * @param identity identity of the arriving connection
	 * @return the holder, or empty when the username is free for the connection
	 */
	public @NotNull Optional<Holder> findHolder(@Nullable UUID connectionUniqueId, @NotNull ConnectionIdentity identity) {
		String key = key(identity.getConnectionUsername());
		if (key == null) return Optional.empty();

		Holder holder = holders.get().getFresh(key).join().orElse(null);
		if (holder == null) return Optional.empty();
		if (Objects.equals(holder.ip, identity.getIp())) return Optional.empty();
		if (isHere(holder) && Objects.equals(holder.connectionUniqueId, connectionUniqueId)) return Optional.empty();

		return Optional.of(holder);
	}

	@IdenticEvent
	public void onScenarioRequired(@NotNull ScenarioRequiredEvent event) {
		ConnectionIdentity identity = event.getContext().getIdentity();
		String key = key(identity.getConnectionUsername());
		long ttlMs = event.getExpiresAt() - System.currentTimeMillis();
		if (key == null || ttlMs <= 0) return;

		holders.get().put(key, new Holder(serverId(), event.getConnectionUniqueId(), identity.getIp()), ttlMs).join();
		heldHere.put(event.getConnectionUniqueId(), key);
		Logger.debug("Username hold taken username=%s connection=%s", key, event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onScenarioResolved(@NotNull ScenarioResolvedEvent event) {
		release(event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onIdentityDetached(@NotNull IdentityDetachedEvent event) {
		release(event.getUniqueId());
	}

	/**
	 * Gives up the hold of a connection of this proxy, unless another connection holds the username by now.
	 */
	private void release(@NotNull UUID connectionUniqueId) {
		String key = heldHere.remove(connectionUniqueId);
		if (key == null) return;

		Holder holder = holders.get().getFresh(key).join().orElse(null);
		if (holder == null || !isHere(holder) || !connectionUniqueId.equals(holder.connectionUniqueId)) return;

		holders.get().invalidate(key).join();
		Logger.debug("Username hold released username=%s connection=%s", key, connectionUniqueId);
	}

	private boolean isHere(@NotNull Holder holder) {
		return serverId().equalsIgnoreCase(holder.serverId == null ? "" : holder.serverId);
	}

	private @NotNull String serverId() {
		return replicationProvider.get().getServerId();
	}

	private static @Nullable String key(@Nullable String username) {
		if (username == null || username.isBlank()) return null;

		return username.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * The connection that holds a username while it logs in.
	 */
	@NoArgsConstructor
	@AllArgsConstructor
	public static final class Holder {
		/**
		 * Replication server id of the proxy that holds the connection.
		 */
		public @Nullable String serverId;
		public @Nullable UUID connectionUniqueId;
		public @Nullable String ip;
	}
}
