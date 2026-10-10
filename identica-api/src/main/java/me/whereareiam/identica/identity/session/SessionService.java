package me.whereareiam.identica.identity.session;

import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
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
 * <p>Sessions are ephemeral and stored in cache-backed storage with
 * cache-level key listings for global visibility.</p>
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
	 * Finds a session by identity id.
	 *
	 * @param uniqueId identity id
	 * @return optional session
	 */
	@NotNull CompletableFuture<Optional<Session>> findByUniqueId(@Nullable UUID uniqueId);

	/**
	 * Finds a session by provider subject.
	 *
	 * @param providerId provider id
	 * @param providerSubject provider subject
	 * @return optional session
	 */
	@NotNull CompletableFuture<Optional<Session>> findByProviderSubject(
			@Nullable String providerId,
			@Nullable String providerSubject
	);

	/**
	 * Opens a session and stores it in the session cache, under the concurrency policy configured for
	 * the session's provider.
	 *
	 * <p>A session from the connection that holds the account's current session continues it. A session
	 * from any other connection is a concurrent login: {@code REPLACE_EXISTING} closes the current session
	 * and disconnects its connection on whichever proxy holds it, {@code REJECT_NEW} refuses the new session
	 * while the current one is live, and {@code ALLOW_MULTIPLE} stores the new session and leaves the other
	 * connection online. A current session counts as gone when its connection is no longer online on this
	 * proxy, or when the proxy that holds it stopped announcing itself.</p>
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
	 * Lists active session ids for the given page.
	 *
	 * @param page page number (1-based)
	 * @param pageSize number of entries per page
	 * @return page result
	 */
	@NotNull CompletableFuture<Page> list(int page, int pageSize);

	/**
	 * Page result for session listings.
	 *
	 * @param entries listed unique ids
	 * @param page current page
	 * @param pageSize page size
	 * @param total total entries
	 */
	record Page(@NotNull List<UUID> entries, int page, int pageSize, int total) {
	}
}
