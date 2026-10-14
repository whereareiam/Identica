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
import me.whereareiam.identica.model.replication.LeaseRenewal;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.Versioned;
import me.whereareiam.identica.replication.lease.LeaseKeeper;
import me.whereareiam.identica.replication.lease.LeasedRecords;
import me.whereareiam.identica.replication.lease.Leases;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, for every username, the connection that is logging in under it, so that a proxy of a network refuses
 * another connection with that username while the first one logs in. A connection holds its username from the moment
 * its login waits for the player until the login ends, the player leaves or the login times out.
 *
 * <p>A hold is a lease of the {@link LeaseKeeper}: the proxy of the connection renews it while the login waits, so
 * the holds of a proxy that stopped or crashed expire within the heartbeat timeout instead of blocking the username
 * until the login would have timed out.</p>
 *
 * <p>A hold is written only over the hold that was read, so of two connections from different addresses that find
 * the username free at the same moment, on two proxies, only one takes it; {@link UsernameHold} disconnects the
 * other.</p>
 *
 * <p>A proxy refuses a second connection of a username that one of its own players uses by itself; the holds matter
 * between the proxies of a replicated network.</p>
 */
@Singleton
public class UsernameHolds implements EventListener {
	private static final String NAMESPACE_SUFFIX = ":username-holds";

	private final Provider<Replication> replicationProvider;
	private final Supplier<ReplicatedCache<Holder>> holders;
	private final LeaseKeeper leaseKeeper;
	private final Leases<Holder> leases;
	private final Map<UUID, String> heldHere = new ConcurrentHashMap<>();

	@Inject
	public UsernameHolds(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull LeaseKeeper leaseKeeper,
			@NotNull EventManager eventManager
	) {
		this.replicationProvider = replicationProvider;
		this.holders = Suppliers.memoize(() -> replicationSystem
				.cache(replicationProvider.get().getCache().getPipelineState() + NAMESPACE_SUFFIX)
				.replicated(ReplicationType.identity(Holder.class)));
		this.leaseKeeper = leaseKeeper;
		this.leases = leaseKeeper.leases("username hold", new HolderRecords());
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

	/**
	 * Takes the hold of the username a connection logs in under, once its login waits for the player. The hold is
	 * written only over what was read: nothing, a hold of the same connection, or a hold from the connection's own
	 * address, which is the same player returning. A hold of a connection from another address is kept unless the
	 * arriving connection was proven to own the account, and of two connections that both found the username free,
	 * only the first to write takes it.
	 *
	 * @param event the login that now waits for the player
	 * @param takeOver whether a provider proved that the connection owns the account, so it takes over a hold of
	 *                 another address
	 * @return {@code false} when another connection holds the username, so this one must not log in under it
	 */
	public boolean take(@NotNull ScenarioRequiredEvent event, boolean takeOver) {
		ConnectionIdentity identity = event.getContext().getIdentity();
		String key = key(identity.getConnectionUsername());
		long ttlMs = Math.min(leaseKeeper.lifetimeMs(), event.getExpiresAt() - System.currentTimeMillis());
		if (key == null || ttlMs <= 0) return true;

		Holder holder = new Holder(serverId(), event.getConnectionUniqueId(), identity.getIp());
		Versioned<Holder> current = holders.get().read(key).join().orElse(null);
		if (current != null && !takeOver && !mayReplace(current.getValue(), holder)) {
			Logger.debug("Username hold refused username=%s connection=%s: held by %s", key, event.getConnectionUniqueId(),
					current.getValue().connectionUniqueId);
			return false;
		}
		if (!holders.get().write(key, current, holder, ttlMs).join()) {
			Logger.debug("Username hold lost username=%s connection=%s: another connection took it meanwhile", key,
					event.getConnectionUniqueId());
			return false;
		}

		leases.hold(key, holder, event.getExpiresAt());
		heldHere.put(event.getConnectionUniqueId(), key);
		Logger.debug("Username hold taken username=%s connection=%s", key, event.getConnectionUniqueId());
		return true;
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

		if (leases.find(key).filter(held -> connectionUniqueId.equals(held.connectionUniqueId)).isPresent())
			leases.release(key);

		Holder holder = holders.get().getFresh(key).join().orElse(null);
		if (holder == null || !isHere(holder) || !connectionUniqueId.equals(holder.connectionUniqueId)) return;

		holders.get().invalidate(key).join();
		Logger.debug("Username hold released username=%s connection=%s", key, connectionUniqueId);
	}

	/**
	 * Whether a connection may replace a stored hold without proof: the hold is its own, or the player holding it
	 * connects from the same address.
	 */
	private static boolean mayReplace(@NotNull Holder stored, @NotNull Holder arriving) {
		return isSameHolder(stored, arriving) || Objects.equals(stored.ip, arriving.ip);
	}

	/**
	 * Whether two holders are the same connection of the same proxy.
	 */
	private static boolean isSameHolder(@NotNull Holder first, @NotNull Holder second) {
		return Objects.equals(first.connectionUniqueId, second.connectionUniqueId)
				&& serverIdOf(first).equalsIgnoreCase(serverIdOf(second));
	}

	private boolean isHere(@NotNull Holder holder) {
		return serverId().equalsIgnoreCase(serverIdOf(holder));
	}

	private static @NotNull String serverIdOf(@NotNull Holder holder) {
		return holder.serverId == null ? "" : holder.serverId;
	}

	private @NotNull String serverId() {
		return replicationProvider.get().getServerId();
	}

	private static @Nullable String key(@Nullable String username) {
		if (username == null || username.isBlank()) return null;

		return username.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Renews the holds of this proxy's connections: a hold that another connection took over meanwhile ends.
	 */
	private final class HolderRecords implements LeasedRecords<Holder> {
		@Override
		public @NotNull CompletableFuture<Optional<Holder>> find(@NotNull String key) {
			return holders.get().getFresh(key);
		}

		@Override
		public boolean isTakenOver(@NotNull Holder held, @NotNull Holder stored) {
			return !isSameHolder(held, stored);
		}

		@Override
		public @NotNull CompletableFuture<Void> write(@NotNull String key, @NotNull Holder holder, @NotNull LeaseRenewal renewal) {
			return holders.get().put(key, holder, renewal.getTtlMs());
		}
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
