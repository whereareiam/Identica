package me.whereareiam.identica.common.identity.session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.event.identity.session.SessionReplacedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.event.EventOrder;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import me.whereareiam.identica.util.UniqueIdUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class DefaultSessionService implements SessionService, EventListener {
	private static final Origin ORIGIN = Origin.core(DefaultSessionService.class);
	private static final Purpose PURPOSE = Purpose.of("live-session-keepalive");

	private final Provider<Settings> settingsProvider;
	private final Provider<Providers> providersProvider;
	private final Provider<Replication> replicationProvider;
	private final Provider<Messages> messagesProvider;
	private final IdentityService identityService;
	private final ServerPresence serverPresence;
	private final EventManager eventManager;
	private final Scheduler scheduler;
	private final long sessionCacheTtlMs;

	private final ReplicatedCache<Session> userCache;
	private final ReplicatedCache<Session> sessionCache;
	private final ReplicatedCache<Session> subjectCache;

	@Inject
	public DefaultSessionService(
			Provider<Settings> settingsProvider,
			Provider<Providers> providersProvider,
			EventManager eventManager,
			Scheduler scheduler,
			Provider<Replication> replicationProvider,
			Provider<Messages> messagesProvider,
			ReplicationSystem replicationSystem,
			IdentityService identityService,
			ServerPresence serverPresence
	) {
		this.settingsProvider = settingsProvider;
		this.providersProvider = providersProvider;
		this.replicationProvider = replicationProvider;
		this.messagesProvider = messagesProvider;
		this.identityService = identityService;
		this.serverPresence = serverPresence;
		this.eventManager = eventManager;
		this.scheduler = scheduler;

		Replication.Sessions sessions = resolveSessions(replicationProvider);
		long defaultTtlMs = settingsProvider.get().getSessions().activeTtlMillis();
		ReplicationType<Session, Session> type = ReplicationType.identity(Session.class);
		this.sessionCacheTtlMs = defaultTtlMs;
		this.userCache = replicationSystem.cache(resolveNamespace(sessions.getUser(), "replication.cache.sessions.user"))
				.defaultTtl(defaultTtlMs)
				.replicated(type);
		this.sessionCache = replicationSystem.cache(resolveNamespace(sessions.getSession(), "replication.cache.sessions.session"))
				.defaultTtl(defaultTtlMs)
				.replicated(type);
		this.subjectCache = replicationSystem.cache(resolveNamespace(sessions.getSubject(), "replication.cache.sessions.subject"))
				.defaultTtl(defaultTtlMs)
				.replicated(type);
		eventManager.register(this);
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findBySessionId(@Nullable String sessionId) {
		return getByKey(sessionCache, keySession(sessionId));
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findByUniqueId(@Nullable UUID uniqueId) {
		return getByKey(userCache, keyUser(uniqueId));
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findByProviderSubject(
			@Nullable String providerId,
			@Nullable String providerSubject
	) {
		return getByKey(subjectCache, keySubject(providerId, providerSubject));
	}

	@Override
	public @NotNull CompletableFuture<@Nullable Session> open(@Nullable Session session) {
		if (session == null)
			return CompletableFuture.completedFuture(null);
		SessionConcurrencyPolicy policy = resolveConcurrencyPolicy(session.getProviderId());
		return open(session, policy);
	}

	@Override
	public @NotNull CompletableFuture<@Nullable Session> open(
			@Nullable Session session,
			@NotNull SessionConcurrencyPolicy policy
	) {
		if (session == null)
			return CompletableFuture.completedFuture(null);
		return getFreshByKey(userCache, keyUser(session.getUniqueId()))
				.thenCompose(existingOptional -> openWithExisting(session, existingOptional.orElse(null), policy));
	}

	@Override
	public @NotNull CompletableFuture<Void> close(@Nullable UUID uniqueId) {
		if (uniqueId == null) return CompletableFuture.completedFuture(null);
		return close(SessionCloseRequest.builder()
				.uniqueId(uniqueId)
				.build());
	}

	@Override
	public @NotNull CompletableFuture<Void> close(@NotNull SessionCloseRequest request) {
		SessionCloseRequest prepared = prepareCloseRequest(request);

		return dispatchClose(prepared);
	}

	@Override
	public @NotNull CompletableFuture<Page> list(int page, int pageSize) {
		return userCache.listKeys(page, pageSize)
				.thenApply(keys -> {
					List<UUID> entries = new ArrayList<>();
					for (String key : keys.getEntries()) {
						UUID uniqueId = UniqueIdUtil.parseUniqueId(key);
						if (uniqueId != null) entries.add(uniqueId);
					}
					return new Page(entries, keys.getPage(), keys.getPageSize(), keys.getTotal());
				});
	}

	private CompletableFuture<Void> putAll(Session session) {
		CompletableFuture<Void> futures = userCache.put(keyUser(session.getUniqueId()), session);

		String sessionIdKey = keySession(session.getSessionId());
		if (sessionIdKey != null) {
			futures = futures.thenCompose(ignored -> sessionCache.put(sessionIdKey, session));
		}

		String subjectKey = keySubject(session.getProviderId(), session.getProviderSubject());
		if (subjectKey != null) {
			futures = futures.thenCompose(ignored -> subjectCache.put(subjectKey, session));
		}

		return futures;
	}

	/**
	 * Removes a session's keys, except where they already point to a newer session of the same account or
	 * provider subject, which another connection stored in the meantime.
	 */
	private CompletableFuture<Void> invalidateKeys(Session session) {
		CompletableFuture<Void> futures = invalidateIfHeld(userCache, keyUser(session.getUniqueId()), session);

		String sessionIdKey = keySession(session.getSessionId());
		if (sessionIdKey != null) {
			futures = futures.thenCompose(ignored -> sessionCache.invalidate(sessionIdKey));
		}

		String subjectKey = keySubject(session.getProviderId(), session.getProviderSubject());
		if (subjectKey != null) {
			futures = futures.thenCompose(ignored -> invalidateIfHeld(subjectCache, subjectKey, session));
		}

		return futures;
	}

	private CompletableFuture<Void> invalidateIfHeld(
			@NotNull ReplicatedCache<Session> cache,
			@Nullable String key,
			@NotNull Session session
	) {
		if (key == null) return CompletableFuture.completedFuture(null);

		return cache.getFresh(key)
				.thenCompose(current -> current.isPresent() && !sameSession(current.get(), session)
						? CompletableFuture.completedFuture(null)
						: cache.invalidate(key));
	}

	/**
	 * Brings this proxy's local copies of a session's keys up to date with the shared store, which the proxy
	 * that closed the session has already changed. Writes nothing to the shared store, so a close received
	 * late never removes a session stored after it.
	 */
	private CompletableFuture<Void> refreshKeys(@NotNull UUID uniqueId, @Nullable Session session) {
		CompletableFuture<Void> futures = userCache.getFresh(keyUser(uniqueId)).thenApply(ignored -> null);
		if (session == null) return futures;

		String sessionIdKey = keySession(session.getSessionId());
		if (sessionIdKey != null) {
			futures = futures.thenCompose(ignored -> sessionCache.getFresh(sessionIdKey)).thenApply(ignored -> null);
		}

		String subjectKey = keySubject(session.getProviderId(), session.getProviderSubject());
		if (subjectKey != null) {
			futures = futures.thenCompose(ignored -> subjectCache.getFresh(subjectKey)).thenApply(ignored -> null);
		}

		return futures;
	}

	@IdenticEvent(EventOrder.LOWEST)
	public void onSessionClosed(@NotNull SessionClosedEvent event) {
		UUID uniqueId = event.getUniqueId();
		Session session = event.getSession();
		if (session == null || keepsAliveHere(session))
			cancelKeepalive(uniqueId);

		if (!isLocalOrigin(event)) {
			refreshKeys(uniqueId, session).join();
			return;
		}

		if (session != null) {
			invalidateKeys(session).join();
			return;
		}

		getFreshByKey(userCache, keyUser(uniqueId))
				.thenCompose(current -> current.isEmpty()
						? userCache.invalidate(keyUser(uniqueId))
						: CompletableFuture.completedFuture(null))
				.join();
	}

	@IdenticEvent
	public void onShutdown(@NotNull IdenticaShutdownEvent event) {
		scheduler.cancelByOrigin(ORIGIN);
	}

	private @NotNull CompletableFuture<Void> dispatchClose(@NotNull SessionCloseRequest request) {
		UUID uniqueId = request.getUniqueId();
		SessionConnection connection = request.getConnection();
		return getFreshByKey(userCache, keyUser(uniqueId))
				.thenAccept(existing -> {
					if (connection != null && existing.map(session -> !session.belongsTo(connection)).orElse(true)) {
						Logger.debug("Session of %s is not held by connection %s; left open", uniqueId, connection);
						return;
					}

					eventManager.call(new SessionClosedEvent(uniqueId, existing.orElse(null), request));
				});
	}

	private @NotNull SessionCloseRequest prepareCloseRequest(@NotNull SessionCloseRequest request) {
		UUID requestId = request.getRequestId() != null
				? request.getRequestId()
				: UUID.randomUUID();
		String originServerId = hasText(request.getOriginServerId())
				? request.getOriginServerId()
				: resolveServerId();

		return request.toBuilder()
				.requestId(requestId)
				.originServerId(originServerId)
				.connection(withServerId(request.getConnection()))
				.build();
	}

	private void prepareSession(Session session) {
		if (session.getSessionId() == null || session.getSessionId().isBlank()) {
			session.setSessionId(UUID.randomUUID().toString());
		}
		if (session.getCreatedAt() <= 0) {
			session.setCreatedAt(System.currentTimeMillis());
		}
		if (session.getEffectiveUsername() == null || session.getEffectiveUsername().isBlank()) {
			session.setEffectiveUsername(session.getOriginalUsername());
		}
	}

	private boolean sameSession(Session existing, Session incoming) {
		if (existing == null || incoming == null) return false;
		String existingId = existing.getSessionId();
		String incomingId = incoming.getSessionId();
		return existingId != null && existingId.equals(incomingId);
	}

	private @NotNull CompletableFuture<@Nullable Session> openWithExisting(
			@NotNull Session incoming,
			@Nullable Session existing,
			@NotNull SessionConcurrencyPolicy policy
	) {
		incoming.setConnection(withServerId(incoming.getConnection()));
		incoming.adoptSessionIdFrom(existing);
		if (existing == null || sameSession(existing, incoming))
			return store(incoming, continuedFrom(existing, incoming));

		boolean live = isLive(existing);
		if (live && policy.rejectsNew()) {
			Logger.debug("Refused a session of %s for %s: session %s of %s is live",
					incoming.getUniqueId(), incoming.getConnection(), existing.getSessionId(), existing.getConnection());
			return CompletableFuture.completedFuture(null);
		}

		prepareSession(incoming);
		boolean replace = live && policy.replacesExisting();
		if (replace)
			eventManager.call(new SessionReplacedEvent(existing, incoming));
		closeConcurrent(existing, replace);
		return store(incoming, CompletableFuture.completedFuture(null));
	}

	private @NotNull CompletableFuture<@Nullable Session> store(
			@NotNull Session session,
			@NotNull CompletableFuture<Void> cleanup
	) {
		prepareSession(session);
		return cleanup
				.thenCompose(ignored -> putAll(session))
				.thenApply(ignored -> {
					if (keepsAliveHere(session))
						scheduleKeepalive(session);
					return session;
				});
	}

	/**
	 * Removes the provider subject key of a continued session whose provider subject changed.
	 */
	private @NotNull CompletableFuture<Void> continuedFrom(@Nullable Session existing, @NotNull Session incoming) {
		if (existing == null) return CompletableFuture.completedFuture(null);

		String previousKey = keySubject(existing.getProviderId(), existing.getProviderSubject());
		if (previousKey == null || previousKey.equals(keySubject(incoming.getProviderId(), incoming.getProviderSubject())))
			return CompletableFuture.completedFuture(null);

		return invalidateIfHeld(subjectCache, previousKey, existing);
	}

	/**
	 * Closes the session of another connection that a new session supersedes. The close is replicated, so
	 * every proxy refreshes its copies and, when replacing, the proxy holding that connection disconnects it.
	 */
	private void closeConcurrent(@NotNull Session existing, boolean disconnect) {
		SessionConnection connection = existing.getConnection();
		SessionCloseRequest request = prepareCloseRequest(SessionCloseRequest.builder()
				.uniqueId(existing.getUniqueId())
				.connection(connection)
				.disconnect(disconnect && connection != null)
				.disconnectMessage(disconnect ? concurrentLoginKick() : null)
				.build());

		eventManager.call(new SessionClosedEvent(existing.getUniqueId(), existing, request));
	}

	/**
	 * Returns whether a session of another connection is still held. A session without a connection is
	 * assumed held. A session held by this proxy is held while its connection is online here; a session held
	 * by another proxy is held while that proxy keeps announcing itself.
	 */
	private boolean isLive(@NotNull Session session) {
		SessionConnection connection = session.getConnection();
		if (connection == null || !hasText(connection.getServerId())) return true;

		if (isHeldHere(session))
			return identityService.findByConnectionUniqueId(connection.getConnectionUniqueId()).isPresent();

		return serverPresence.isRunning(connection.getServerId());
	}

	/**
	 * Returns whether this proxy refreshes a session: the proxy holding its connection does, and a session
	 * opened without a connection is refreshed where it was opened.
	 */
	private boolean keepsAliveHere(@NotNull Session session) {
		return session.getConnection() == null || isHeldHere(session);
	}

	private boolean isHeldHere(@NotNull Session session) {
		SessionConnection connection = session.getConnection();
		return connection != null
				&& hasText(connection.getServerId())
				&& connection.getServerId().trim().equalsIgnoreCase(resolveServerId().trim());
	}

	private boolean isLocalOrigin(@NotNull SessionClosedEvent event) {
		String origin = event.getReplicationOriginServerId();
		return !hasText(origin) || origin.trim().equalsIgnoreCase(resolveServerId().trim());
	}

	private @Nullable SessionConnection withServerId(@Nullable SessionConnection connection) {
		if (connection == null || hasText(connection.getServerId())) return connection;

		return connection.toBuilder()
				.serverId(resolveServerId())
				.build();
	}

	private @NotNull String concurrentLoginKick() {
		return String.join("\n", messagesProvider.get().getEngine().getConcurrentLoginKick());
	}

	private SessionConcurrencyPolicy resolveConcurrencyPolicy(@Nullable String providerId) {
		Providers.ProviderEntry provider = findProvider(providerId);
		Providers.ProviderEntry.Session session = provider != null
				? provider.getSession()
				: null;
		SessionConcurrencyPolicy override = session != null
				? session.getConcurrencyPolicy()
				: null;

		return override != null
				? override
				: settingsProvider.get().getSessions().getConcurrencyPolicy();
	}

	private @Nullable Providers.ProviderEntry findProvider(@Nullable String rawId) {
		String id = trimToNull(rawId);
		if (id == null) return null;

		Providers config = providersProvider.get();
		for (Providers.ProviderEntry entry : config.getProviders()) {
			if (entry == null) continue;
			String entryId = trimToNull(entry.getId());
			if (entryId != null && entryId.equalsIgnoreCase(id))
				return entry;
		}

		return null;
	}

	private String keySession(String sessionId) {
		return trimToNull(sessionId);
	}

	private String keyUser(UUID uniqueId) {
		if (uniqueId == null) return null;
		return uniqueId.toString();
	}

	private String keySubject(String providerId, String providerSubject) {
		String normalizedProviderId = normalize(providerId);
		String normalizedProviderSubject = normalize(providerSubject);
		if (normalizedProviderId == null || normalizedProviderSubject == null)
			return null;

		return normalizedProviderId + ":" + normalizedProviderSubject;
	}

	private @Nullable String normalize(@Nullable String value) {
		String trimmed = trimToNull(value);
		return trimmed != null
				? trimmed.toLowerCase()
				: null;
	}

	private @Nullable String trimToNull(@Nullable String value) {
		if (value == null)
			return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private boolean hasText(@Nullable String value) {
		return value != null && !value.trim().isEmpty();
	}

	private @NotNull CompletableFuture<Optional<Session>> getByKey(
			@NotNull ReplicatedCache<Session> cache,
			@Nullable String key
	) {
		if (key == null)
			return CompletableFuture.completedFuture(Optional.empty());
		return cache.get(key);
	}

	private @NotNull CompletableFuture<Optional<Session>> getFreshByKey(
			@NotNull ReplicatedCache<Session> cache,
			@Nullable String key
	) {
		if (key == null)
			return CompletableFuture.completedFuture(Optional.empty());
		return cache.getFresh(key);
	}

	private void scheduleKeepalive(@NotNull Session session) {
		UUID uniqueId = session.getUniqueId();

		long intervalMs = keepaliveIntervalMs();
		if (intervalMs <= 0)
			return;

		scheduler.schedule(PeriodicalRunnableTask.builder()
				.key(jobKey(uniqueId))
				.delay(intervalMs)
				.period(intervalMs)
				.runnable(() -> refreshLiveSession(uniqueId, session.getSessionId()))
				.build());
	}

	/**
	 * Refreshes the session this keepalive was scheduled for, and stops once the account's session is gone
	 * or belongs to another connection.
	 */
	private void refreshLiveSession(@NotNull UUID uniqueId, @Nullable String sessionId) {
		getFreshByKey(userCache, keyUser(uniqueId))
				.thenCompose(existing -> existing
						.filter(session -> sessionId != null && sessionId.equals(session.getSessionId()))
						.map(this::putAll)
						.orElseGet(() -> {
							cancelKeepalive(uniqueId);
							return CompletableFuture.completedFuture(null);
						}))
				.join();
	}

	private void cancelKeepalive(@Nullable UUID uniqueId) {
		if (uniqueId == null) return;
		scheduler.cancel(jobKey(uniqueId));
	}

	private long keepaliveIntervalMs() {
		if (sessionCacheTtlMs <= 0) return 0L;
		return Math.max(1000L, sessionCacheTtlMs / 2L);
	}

	private @NotNull JobKey jobKey(@NotNull UUID uniqueId) {
		return JobKey.of(ORIGIN, PURPOSE, uniqueId.toString());
	}

	private static Replication.Sessions resolveSessions(Provider<Replication> replicationProvider) {
		Replication replication = replicationProvider.get();
		if (replication == null)
			throw new IllegalStateException("replication is missing");

		return replication.getCache().getSessions();
	}

	private @NotNull String resolveServerId() {
		Replication replication = replicationProvider.get();
		if (replication == null) return "";

		return replication.getServerId();
	}

	private static String resolveNamespace(String namespace, String label) {
		if (namespace == null || namespace.isBlank())
			throw new IllegalStateException(label + " is missing");

		return namespace;
	}
}
