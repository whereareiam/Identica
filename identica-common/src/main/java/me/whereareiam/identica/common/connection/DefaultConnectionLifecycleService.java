package me.whereareiam.identica.common.connection;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionProcessAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionResumeAttemptEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DefaultConnectionLifecycleService implements ConnectionLifecycleService, EventListener {
	private static final String NAMESPACE = "identica:connection-lifecycle";

	private final EventManager eventManager;
	private final ReplicatedCache<ConnectionLifecycleRecord> cache;
	private final Provider<Settings> settingsProvider;
	private final Map<UUID, UUID> currentTokens = new ConcurrentHashMap<>();

	@Inject
	public DefaultConnectionLifecycleService(
			@NotNull EventManager eventManager,
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Settings> settingsProvider
	) {
		this.eventManager = eventManager;
		this.cache = replicationSystem.cache(NAMESPACE)
				.defaultTtl(settingsProvider.get().getSessions().activeTtlMillis())
				.replicated(ReplicationType.identity(ConnectionLifecycleRecord.class));
		this.settingsProvider = settingsProvider;
		eventManager.register(this);
	}

	@Override
	public void completed(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	) {
		transition(connectionUniqueId, ConnectionLifecycleRecord.Status.COMPLETED, () ->
				eventManager.call(new ConnectionCompletedEvent(connectionUniqueId, accountUniqueId, pipelineType)));
	}

	@Override
	public void disconnected(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	) {
		eventManager.call(new ConnectionDisconnectedEvent(connectionUniqueId, accountUniqueId, pipelineType));
	}

	@Override
	public void terminated(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	) {
		transition(connectionUniqueId, ConnectionLifecycleRecord.Status.TERMINATED, () ->
				eventManager.call(new ConnectionTerminatedEvent(connectionUniqueId, accountUniqueId, pipelineType)));
	}

	@IdenticEvent
	public void onConnectionProcessAttempt(@NotNull ConnectionProcessAttemptEvent event) {
		reopen(event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onConnectionResumeAttempt(@NotNull ConnectionResumeAttemptEvent event) {
		reopen(event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onIdentityDetached(@NotNull IdentityDetachedEvent event) {
		currentTokens.remove(event.getUniqueId());
		cache.invalidate(key(event.getUniqueId())).join();
	}

	private void reopen(@Nullable UUID connectionUniqueId) {
		if (connectionUniqueId == null) return;
		UUID token = UUID.randomUUID();
		currentTokens.put(connectionUniqueId, token);
		cache.put(
				key(connectionUniqueId),
				new ConnectionLifecycleRecord(token, ConnectionLifecycleRecord.Status.OPEN),
				ttlMs()
		).join();
	}

	private void transition(
			@NotNull UUID connectionUniqueId,
			@NotNull ConnectionLifecycleRecord.Status target,
			@NotNull Runnable emitter
	) {
		UUID token = currentTokens.get(connectionUniqueId);
		if (token == null) return;

		Optional<ConnectionLifecycleRecord> existing = cache.getFresh(key(connectionUniqueId)).join();
		ConnectionLifecycleRecord record = existing.orElse(null);
		if (record == null) return;
		if (!token.equals(record.token())) return;
		if (record.status() != ConnectionLifecycleRecord.Status.OPEN) return;

		cache.put(
				key(connectionUniqueId),
				new ConnectionLifecycleRecord(token, target),
				ttlMs()
		).join();
		emitter.run();
	}

	private long ttlMs() {
		return settingsProvider.get().getSessions().activeTtlMillis();
	}

	private @NotNull String key(@NotNull UUID connectionUniqueId) {
		return connectionUniqueId.toString();
	}

	private record ConnectionLifecycleRecord(@NotNull UUID token, @NotNull Status status) {
		private enum Status {
			OPEN,
			COMPLETED,
			TERMINATED
		}
	}
}
