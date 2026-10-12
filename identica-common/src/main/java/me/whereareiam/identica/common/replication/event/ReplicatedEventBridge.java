package me.whereareiam.identica.common.replication.event;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.event.base.ReplicatedEvent;
import me.whereareiam.identica.event.account.AccountClearEvent;
import me.whereareiam.identica.event.account.AccountDeleteEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationChannel;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.SnapshotMapper;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.event.ReplicatedEventRegistry;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passes the replicated events between the proxies of a network: publishes the ones raised here and calls the ones
 * received from the others. It subscribes to the events channel in {@link StartupPhase#RUNTIME}, once replication
 * is open; until then it publishes nothing.
 */
@Singleton
public class ReplicatedEventBridge implements EventListener, RuntimeLifecycle {
	private final @NotNull EventManager eventManager;
	private final @NotNull ReplicationSystem replicationSystem;
	private final @NotNull Provider<Replication> replicationProvider;
	private final @NotNull ReplicatedEventRegistry registry;
	private final @NotNull Set<UUID> processedEvents = ConcurrentHashMap.newKeySet();
	private final @NotNull ThreadLocal<Boolean> receiving = ThreadLocal.withInitial(() -> false);
	private volatile ReplicationChannel<ReplicatedEvent> channel;

	@Inject
	public ReplicatedEventBridge(
			@NotNull EventManager eventManager,
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull ReplicatedEventRegistry registry
	) {
		this.eventManager = eventManager;
		this.replicationSystem = replicationSystem;
		this.replicationProvider = replicationProvider;
		this.registry = registry;

		registerBuiltInEvents();
		eventManager.register(this);
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	@Override
	public void initialize() {
		initializeChannel();
	}

	/**
	 * Stops publishing; the subscription ends with the replication connection.
	 */
	@Override
	public void shutdown() {
		channel = null;
	}

	@IdenticEvent(EventOrder.HIGHEST)
	public void onReplicatedEvent(@NotNull ReplicatedEvent event) {
		ReplicationChannel<ReplicatedEvent> channel = this.channel;
		if (receiving.get()) return;
		if (channel == null) return;
		if (registry.typeOf(event.getClass()).isEmpty()) return;

		prepare(event);
		if (!remember(event.getReplicationEventId())) return;

		channel.publish(event);
	}

	private void initializeChannel() {
		String channelName = resolveChannelName();
		if (channelName == null) return;

		ReplicationType<ReplicatedEvent, ReplicatedEventSnapshot> type = ReplicationType.ofSnapshot(
				ReplicatedEventSnapshot.class,
				new EventSnapshotMapper()
		);
		ReplicationChannel<ReplicatedEvent> channel = replicationSystem.channel(channelName, type);
		channel.subscribe(this::receive);
		this.channel = channel;
	}

	private void receive(@NotNull ReplicatedEvent event) {
		if (isLocalOrigin(event)) return;
		if (!remember(event.getReplicationEventId())) return;

		receiving.set(true);
		try {
			eventManager.call(event);
		} finally {
			receiving.remove();
		}
	}

	private void registerBuiltInEvents() {
		registry.register("session-closed", SessionClosedEvent.class);
		registry.register("account-clear", AccountClearEvent.class);
		registry.register("account-delete", AccountDeleteEvent.class);
	}

	private void prepare(@NotNull ReplicatedEvent event) {
		if (event.getReplicationEventId() == null)
			event.setReplicationEventId(UUID.randomUUID());
		if (!hasText(event.getReplicationOriginServerId()))
			event.setReplicationOriginServerId(resolveServerId());
	}

	private boolean remember(@Nullable UUID eventId) {
		return eventId != null && processedEvents.add(eventId);
	}

	private boolean isLocalOrigin(@NotNull ReplicatedEvent event) {
		String localServerId = resolveServerId();
		String originServerId = event.getReplicationOriginServerId();
		return hasText(localServerId)
				&& hasText(originServerId)
				&& localServerId.equalsIgnoreCase(originServerId);
	}

	private @Nullable String resolveChannelName() {
		Replication replication = replicationProvider.get();

		Replication.Channels channels = replication.getRedis().getChannels();
		if (hasText(channels.getEvents())) return channels.getEvents();
		if (hasText(channels.getSessions())) return channels.getSessions();

		return null;
	}

	private @NotNull String resolveServerId() {
		Replication replication = replicationProvider.get();
		if (replication == null) return "";

		return replication.getServerId();
	}

	private boolean hasText(@Nullable String value) {
		return value != null && !value.trim().isEmpty();
	}

	private final class EventSnapshotMapper implements SnapshotMapper<ReplicatedEvent, ReplicatedEventSnapshot> {
		@Override
		public ReplicatedEventSnapshot toSnapshot(@NotNull ReplicatedEvent event) {
			Optional<String> type = registry.typeOf(event.getClass());
			if (type.isEmpty()) return new ReplicatedEventSnapshot();

			SnapshotCodec<ReplicatedEvent> codec = codecFor(event.getClass());
			String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
			return new ReplicatedEventSnapshot(
					type.get(),
					event.getReplicationEventId(),
					event.getReplicationOriginServerId(),
					payload
			);
		}

		@Override
		public @NotNull CompletableFuture<ReplicatedEvent> fromSnapshot(@NotNull ReplicatedEventSnapshot snapshot) {
			if (!hasText(snapshot.getType()) || !hasText(snapshot.getPayload()))
				return CompletableFuture.completedFuture(null);

			Optional<Class<? extends ReplicatedEvent>> eventClass = registry.classOf(snapshot.getType());
			if (eventClass.isEmpty()) return CompletableFuture.completedFuture(null);

			try {
				SnapshotCodec<? extends ReplicatedEvent> codec = codecFor(eventClass.get());
				ReplicatedEvent event = codec.decode(snapshot.getPayload().getBytes(StandardCharsets.UTF_8));
				if (event == null) return CompletableFuture.completedFuture(null);

				event.setReplicationEventId(snapshot.getEventId());
				event.setReplicationOriginServerId(snapshot.getOriginServerId());
				return CompletableFuture.completedFuture(event);
			} catch (Exception e) {
				Logger.debug("Failed to decode replicated event %s: %s", snapshot.getType(), e.getMessage());
				return CompletableFuture.completedFuture(null);
			}
		}

		@SuppressWarnings("unchecked")
		private <T extends ReplicatedEvent> SnapshotCodec<T> codecFor(@NotNull Class<?> eventClass) {
			return (SnapshotCodec<T>) replicationSystem.getDefaultCodecFactory().codecFor(eventClass);
		}
	}
}
