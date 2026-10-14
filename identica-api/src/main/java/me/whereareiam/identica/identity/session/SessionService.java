package me.whereareiam.identica.identity.session;

import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Central service for session lifecycle and runtime session listings.
 *
 * <p>A session is stored once, under its own id, for as long as the proxy holding its connection keeps
 * refreshing it; the sessions of a proxy that stopped disappear after
 * {@code replication.heartbeatTimeout}. An account has one active session, held by the connection
 * that opened it; a login from another connection replaces it or is refused, as the concurrency policy
 * says.</p>
 */
@SuppressWarnings("unused")
public interface SessionService {
	/**
	 * Finds a session by session id.
	 *
	 * @param sessionId session id
	 * @return optional session
	 */
	@NotNull CompletableFuture<Optional<Session>> findBySessionId(@Nullable String sessionId);

	/**
	 * Finds the session of an account. Use it to learn whether an account is online or how it logged in;
	 * a caller acting for one connection asks {@link #findByConnection(UUID, SessionConnection)}, since a
	 * connection whose session was replaced no longer holds the account's session.
	 *
	 * @param uniqueId account id
	 * @return the account's session
	 */
	@NotNull CompletableFuture<Optional<Session>> findByUniqueId(@Nullable UUID uniqueId);

	/**
	 * Finds the session of an account when one connection holds it.
	 *
	 * <pre>{@code
	 * sessionService.findByConnection(accountUniqueId, SessionConnection.of(player.getUniqueId()));
	 * }</pre>
	 *
	 * @param uniqueId account id
	 * @param connection connection holding the session; a missing server id means this proxy
	 * @return the account's session, when that connection holds it
	 */
	@NotNull CompletableFuture<Optional<Session>> findByConnection(
			@Nullable UUID uniqueId,
			@NotNull SessionConnection connection
	);

	/**
	 * Finds the session a provider subject opened last.
	 *
	 * @param providerId provider id
	 * @param providerSubject provider subject
	 * @return the subject's newest session
	 */
	@NotNull CompletableFuture<Optional<Session>> findByProviderSubject(
			@Nullable String providerId,
			@Nullable String providerSubject
	);

	/**
	 * Opens a session and stores it in the session cache, under the concurrency policy configured for
	 * the session's provider.
	 *
	 * <p>A session from the connection that already holds the account's session continues it. A session
	 * from any other connection is a concurrent login: {@code REPLACE_EXISTING} closes the other
	 * connection's session and disconnects that connection on whichever proxy holds it, and
	 * {@code REJECT_NEW} refuses the new session while the other one exists. A stored session whose
	 * connection is held by this proxy and is no longer online here counts as gone and is removed.</p>
	 *
	 * <pre>{@code
	 * Session stored = sessionService.open(session).join();
	 * }</pre>
	 *
	 * @param session session to open
	 * @return stored session or {@code null} when input is invalid
	 */
	@NotNull CompletableFuture<@Nullable Session> open(@Nullable Session session);

	/**
	 * Opens a session with an explicit concurrency policy override.
	 *
	 * @param session session to open
	 * @param policy concurrency policy override
	 * @return stored session or {@code null} when rejected
	 */
	@NotNull CompletableFuture<@Nullable Session> open(
			@Nullable Session session,
			@NotNull SessionConcurrencyPolicy policy
	);

	/**
	 * Closes the session of an account, whatever connection holds it.
	 *
	 * @param uniqueId identity id
	 * @return completion journey
	 */
	@NotNull CompletableFuture<Void> close(@Nullable UUID uniqueId);

	/**
	 * Closes a session using an explicit replicated request.
	 *
	 * <p>Use this overload when the caller wants every instance to receive the
	 * same disconnect message or request metadata, or to close only the session of one
	 * connection through {@link SessionCloseRequest#getConnection()}.</p>
	 *
	 * @param request close request
	 * @return completion journey
	 */
	@NotNull CompletableFuture<Void> close(@NotNull SessionCloseRequest request);

	/**
	 * Lists the ids of the stored sessions for the given page. Resolve an id with
	 * {@link #findBySessionId(String)}; a session that ended since the listing is no longer found.
	 *
	 * @param page page number (1-based)
	 * @param pageSize number of entries per page
	 * @return page result
	 */
	@NotNull CompletableFuture<Page> list(int page, int pageSize);

	/**
	 * Page result for session listings.
	 *
	 * @param entries listed session ids
	 * @param page current page
	 * @param pageSize page size
	 * @param total total entries
	 */
	record Page(@NotNull List<String> entries, int page, int pageSize, int total) {
	}
}
