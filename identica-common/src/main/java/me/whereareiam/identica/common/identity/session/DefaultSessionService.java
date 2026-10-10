package me.whereareiam.identica.common.identity.session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.event.identity.session.SessionReplacedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.type.event.EventOrder;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Decides what opening and closing a session means: which stored session a login continues, what the
 * concurrency policy does to the sessions of other connections, and which sessions a close ends.
 * {@link SessionStore} keeps the sessions and {@link SessionKeepalive} keeps the ones held here alive.
 */
@Singleton
public class DefaultSessionService implements SessionService, EventListener {
	private final Provider<Settings> settingsProvider;
	private final Provider<Providers> providersProvider;
	private final Provider<Replication> replicationProvider;
	private final Provider<Messages> messagesProvider;
	private final IdentityService identityService;
	private final EventManager eventManager;
	private final SessionStore store;
	private final SessionKeepalive keepalive;

	@Inject
	public DefaultSessionService(
			Provider<Settings> settingsProvider,
			Provider<Providers> providersProvider,
			Provider<Replication> replicationProvider,
			Provider<Messages> messagesProvider,
			IdentityService identityService,
			EventManager eventManager,
			SessionStore store,
			SessionKeepalive keepalive
	) {
		this.settingsProvider = settingsProvider;
		this.providersProvider = providersProvider;
		this.replicationProvider = replicationProvider;
		this.messagesProvider = messagesProvider;
		this.identityService = identityService;
		this.eventManager = eventManager;
		this.store = store;
		this.keepalive = keepalive;
		eventManager.register(this);
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findBySessionId(@Nullable String sessionId) {
		return store.find(sessionId);
	}

	@Override
	public @NotNull CompletableFuture<List<Session>> findAllByUniqueId(@Nullable UUID uniqueId) {
		return store.findByAccount(uniqueId);
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findByUniqueId(@Nullable UUID uniqueId) {
		return store.findByAccount(uniqueId).thenApply(DefaultSessionService::newest);
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findByConnection(
			@Nullable UUID uniqueId,
			@NotNull SessionConnection connection
	) {
		SessionConnection held = withServerId(connection);
		return store.findByAccount(uniqueId)
				.thenApply(sessions -> sessions.stream()
						.filter(session -> session.belongsTo(held))
						.findFirst());
	}

	@Override
	public @NotNull CompletableFuture<Optional<Session>> findByProviderSubject(
			@Nullable String providerId,
			@Nullable String providerSubject
	) {
		return store.findBySubject(providerId, providerSubject).thenApply(DefaultSessionService::newest);
	}

	@Override
	public @NotNull CompletableFuture<@Nullable Session> open(@Nullable Session session) {
		if (session == null)
			return CompletableFuture.completedFuture(null);
		return open(session, resolveConcurrencyPolicy(session.getProviderId()));
	}

	@Override
	public @NotNull CompletableFuture<@Nullable Session> open(
			@Nullable Session session,
			@NotNull SessionConcurrencyPolicy policy
	) {
		if (session == null)
			return CompletableFuture.completedFuture(null);
		return store.findByAccount(session.getUniqueId())
				.thenCompose(existing -> open(session, existing, policy));
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
		SessionCloseRequest prepared = prepare(request);
		SessionConnection connection = prepared.getConnection();

		return store.findByAccount(prepared.getUniqueId()).thenCompose(sessions -> {
			if (connection != null) {
				List<Session> own = sessions.stream().filter(session -> session.belongsTo(connection)).toList();
				if (own.isEmpty())
					Logger.debug("Connection %s holds no session of %s; nothing closed", connection, prepared.getUniqueId());
				return closeAll(own, prepared);
			}

			if (sessions.isEmpty()) {
				eventManager.call(new SessionClosedEvent(prepared.getUniqueId(), null, prepared));
				return CompletableFuture.completedFuture(null);
			}
			return closeAll(sessions, prepared);
		});
	}

	@Override
	public @NotNull CompletableFuture<Page> list(int page, int pageSize) {
		return store.list(page, pageSize)
				.thenApply(keys -> new Page(List.copyOf(keys.getEntries()), keys.getPage(), keys.getPageSize(), keys.getTotal()));
	}

	/**
	 * Stops refreshing a closed session on the proxy holding it. That proxy also removes the record again:
	 * its keepalive may have written it between the removal on the closing proxy and this event.
	 */
	@IdenticEvent(EventOrder.LOWEST)
	public void onSessionClosed(@NotNull SessionClosedEvent event) {
		Session session = event.getSession();
		if (session == null || session.getSessionId() == null || !keepalive.holds(session.getSessionId())) return;

		keepalive.release(session.getSessionId());
		if (!isLocalOrigin(event))
			store.remove(session).join();
	}

	private @NotNull CompletableFuture<@Nullable Session> open(
			@NotNull Session incoming,
			@NotNull List<Session> existing,
			@NotNull SessionConcurrencyPolicy policy
	) {
		incoming.setConnection(withServerId(incoming.getConnection()));
		Session continued = continued(incoming, existing);
		if (continued != null && incoming.getSessionId() == null)
			incoming.adoptSessionIdFrom(continued);

		List<Session> others = new ArrayList<>();
		List<Session> gone = new ArrayList<>();
		for (Session session : existing) {
			if (session == continued) continue;
			(isHeld(session) ? others : gone).add(session);
		}

		if (!others.isEmpty() && policy.rejectsNew()) {
			Logger.debug("Refused a session of %s for %s: %s other connection(s) hold one",
					incoming.getUniqueId(), incoming.getConnection(), others.size());
			return CompletableFuture.completedFuture(null);
		}

		complete(incoming);
		CompletableFuture<Void> closed = closeAll(gone, concurrentClose(incoming, false));
		if (policy.replacesExisting()) {
			for (Session other : others)
				eventManager.call(new SessionReplacedEvent(other, incoming));
			closed = closed.thenCompose(ignored -> closeAll(others, concurrentClose(incoming, true)));
		}

		return closed
				.thenCompose(ignored -> unlistPreviousSubject(continued, incoming))
				.thenCompose(ignored -> store.put(incoming))
				.thenApply(ignored -> {
					if (refreshesHere(incoming)) keepalive.hold(incoming);
					return incoming;
				});
	}

	/**
	 * Returns the stored session a new one continues: the one it names by id, or the one its own
	 * connection holds.
	 */
	private @Nullable Session continued(@NotNull Session incoming, @NotNull List<Session> existing) {
		for (Session session : existing) {
			if (incoming.getSessionId() != null && incoming.getSessionId().equals(session.getSessionId())) return session;
		}
		if (incoming.getSessionId() != null) return null;

		for (Session session : existing) {
			if (session.belongsTo(incoming.getConnection())) return session;
		}
		return null;
	}

	/**
	 * Closes sessions one by one. Every session gets its own replicated event that names its connection,
	 * so a disconnect reaches exactly the connections whose sessions end.
	 */
	private @NotNull CompletableFuture<Void> closeAll(@NotNull List<Session> sessions, @NotNull SessionCloseRequest request) {
		CompletableFuture<Void> closed = CompletableFuture.completedFuture(null);
		boolean first = true;
		for (Session session : sessions) {
			SessionCloseRequest own = request.toBuilder()
					.requestId(first ? request.getRequestId() : UUID.randomUUID())
					.connection(session.getConnection() != null ? session.getConnection() : request.getConnection())
					.build();
			first = false;
			closed = closed
					.thenCompose(ignored -> store.remove(session))
					.thenRun(() -> eventManager.call(new SessionClosedEvent(session.getUniqueId(), session, own)));
		}
		return closed;
	}

	private @NotNull SessionCloseRequest concurrentClose(@NotNull Session incoming, boolean disconnect) {
		return prepare(SessionCloseRequest.builder()
				.uniqueId(incoming.getUniqueId())
				.disconnect(disconnect)
				.disconnectMessage(disconnect ? String.join("\n", messagesProvider.get().getEngine().getConcurrentLoginKick()) : null)
				.build());
	}

	private @NotNull CompletableFuture<Void> unlistPreviousSubject(@Nullable Session continued, @NotNull Session incoming) {
		if (continued == null) return CompletableFuture.completedFuture(null);
		if (same(continued.getProviderId(), incoming.getProviderId()) && same(continued.getProviderSubject(), incoming.getProviderSubject()))
			return CompletableFuture.completedFuture(null);

		return store.unlistSubject(continued, continued.getProviderId(), continued.getProviderSubject());
	}

	/**
	 * Returns whether a stored session of another connection still stands. Its record exists, so the proxy
	 * holding it is refreshing it or stopped less than the heartbeat timeout ago; only this proxy can tell
	 * that a connection it holds is no longer online.
	 */
	private boolean isHeld(@NotNull Session session) {
		SessionConnection connection = session.getConnection();
		if (connection == null || !isHere(connection)) return true;

		return identityService.findByConnectionUniqueId(connection.getConnectionUniqueId()).isPresent();
	}

	/**
	 * Returns whether this proxy refreshes a session: the proxy holding its connection does, and a session
	 * opened without a connection is refreshed where it was opened.
	 */
	private boolean refreshesHere(@NotNull Session session) {
		return session.getConnection() == null || isHere(session.getConnection());
	}

	private boolean isHere(@NotNull SessionConnection connection) {
		return same(connection.getServerId(), serverId());
	}

	private boolean isLocalOrigin(@NotNull SessionClosedEvent event) {
		String origin = event.getReplicationOriginServerId();
		return origin == null || origin.isBlank() || same(origin, serverId());
	}

	private void complete(@NotNull Session session) {
		if (session.getSessionId() == null || session.getSessionId().isBlank())
			session.setSessionId(UUID.randomUUID().toString());
		if (session.getCreatedAt() <= 0)
			session.setCreatedAt(System.currentTimeMillis());
		if (session.getEffectiveUsername() == null || session.getEffectiveUsername().isBlank())
			session.setEffectiveUsername(session.getOriginalUsername());
	}

	private @NotNull SessionCloseRequest prepare(@NotNull SessionCloseRequest request) {
		String origin = request.getOriginServerId();
		return request.toBuilder()
				.requestId(request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID())
				.originServerId(origin != null && !origin.isBlank() ? origin : serverId())
				.connection(withServerId(request.getConnection()))
				.build();
	}

	private @Nullable SessionConnection withServerId(@Nullable SessionConnection connection) {
		if (connection == null) return null;
		if (connection.getServerId() != null && !connection.getServerId().isBlank()) return connection;

		return connection.toBuilder()
				.serverId(serverId())
				.build();
	}

	private @NotNull String serverId() {
		return replicationProvider.get().getServerId();
	}

	private SessionConcurrencyPolicy resolveConcurrencyPolicy(@Nullable String providerId) {
		if (providerId != null) {
			for (Providers.ProviderEntry entry : providersProvider.get().getProviders()) {
				if (entry == null || entry.getId() == null || !entry.getId().trim().equalsIgnoreCase(providerId.trim())) continue;
				if (entry.getSession() != null && entry.getSession().getConcurrencyPolicy() != null)
					return entry.getSession().getConcurrencyPolicy();
			}
		}

		return settingsProvider.get().getSessions().getConcurrencyPolicy();
	}

	private static @NotNull Optional<Session> newest(@NotNull List<Session> sessions) {
		return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.getLast());
	}

	private static boolean same(@Nullable String left, @Nullable String right) {
		return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
	}
}
