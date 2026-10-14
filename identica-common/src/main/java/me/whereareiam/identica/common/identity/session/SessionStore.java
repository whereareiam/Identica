package me.whereareiam.identica.common.identity.session;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.Versioned;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.replication.lease.LeaseKeeper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Stores each session once, as a record under its own id, and finds it again by account and by provider
 * subject through index entries that only list session ids. This is the only code that knows the keys.
 * <p>
 * The record is the truth. It lives for the lease lifetime, the heartbeat timeout, and has to be refreshed by
 * the {@link SessionKeepalive} to stay, so the sessions of a proxy that stopped disappear by themselves. An index entry may be out of date: a lookup
 * resolves every id it lists against the records, returns only the sessions that exist and still belong
 * under that key, and drops the other ids from the entry. Nothing is read from the proxy's local copies
 * while the shared store is available, so every proxy sees a session appear and end at once.
 */
@Singleton
public class SessionStore {
	/** An index entry outlives the records it lists by this factor; it is renewed with {@link #index}. */
	static final int INDEX_LIFETIMES = 10;
	/** How often a change to an index entry is applied again when other proxies keep changing the entry first. */
	static final int CHANGE_ATTEMPTS = 5;

	private static final String SEPARATOR = "\n";
	/** Sessions created in the same millisecond keep the order in which the index entry lists them. */
	private static final Comparator<Session> OLDEST_FIRST = Comparator.comparingLong(Session::getCreatedAt);

	private final Supplier<Stored> stored;

	@Inject
	public SessionStore(
			LeaseKeeper leaseKeeper,
			Provider<Replication> replicationProvider,
			ReplicationSystem replicationSystem
	) {
		this.stored = Suppliers.memoize(() -> Stored.of(
				leaseKeeper.lifetimeMs(),
				replicationProvider.get(),
				replicationSystem
		));
	}

	SessionStore(long lifetimeMs, Replication replication, ReplicationSystem replicationSystem) {
		this.stored = Suppliers.ofInstance(Stored.of(lifetimeMs, replication, replicationSystem));
	}

	public @NotNull CompletableFuture<Optional<Session>> find(@Nullable String sessionId) {
		String key = trimToNull(sessionId);
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());

		return stored.get().records().getFresh(key);
	}

	/**
	 * Returns the sessions of an account, oldest first.
	 */
	public @NotNull CompletableFuture<List<Session>> findByAccount(@Nullable UUID uniqueId) {
		return account(uniqueId, true).thenApply(AccountSessions::sessions);
	}

	/**
	 * Returns the sessions of an account together with the index entry they were read from, which
	 * {@link #commit} takes to change the account's sessions only while nobody else did. Ids whose session is gone
	 * are left in the entry, since the commit writes the entry anyway and dropping them first would make it fail.
	 */
	public @NotNull CompletableFuture<AccountSessions> account(@Nullable UUID uniqueId) {
		return account(uniqueId, false);
	}

	private @NotNull CompletableFuture<AccountSessions> account(@Nullable UUID uniqueId, boolean prune) {
		String key = accountKey(uniqueId);
		if (key == null) return CompletableFuture.completedFuture(new AccountSessions(List.of(), null));

		return stored.get().accounts().read(key).thenCompose(entry -> resolve(
				stored.get().accounts(),
				key,
				entry.orElse(null),
				session -> key.equals(accountKey(session.getUniqueId())),
				prune
		).thenApply(sessions -> new AccountSessions(sessions, entry.orElse(null))));
	}

	/**
	 * Sets the sessions an account is listed with, only while its index entry is still as it was read. This is the
	 * step that makes a login count: of two logins that read the same sessions, only the first commits and the
	 * other is refused. Records are not written here; write the new session's record first, so the
	 * entry never names a session whose record is still missing.
	 *
	 * @param uniqueId account id
	 * @param read the account's sessions as read by {@link #account}
	 * @param sessionIds ids the account is listed with afterwards
	 * @return {@code true} when committed, {@code false} when the account's sessions changed since they were read
	 */
	public @NotNull CompletableFuture<Boolean> commit(
			@NotNull UUID uniqueId,
			@NotNull AccountSessions read,
			@NotNull Set<String> sessionIds
	) {
		String key = accountKey(uniqueId);
		if (sessionIds.isEmpty())
			return read.entry() != null ? stored.get().accounts().remove(key, read.entry()) : CompletableFuture.completedFuture(true);

		return stored.get().accounts().write(key, read.entry(), String.join(SEPARATOR, sessionIds));
	}

	/**
	 * Returns the sessions of a provider subject, oldest first.
	 */
	public @NotNull CompletableFuture<List<Session>> findBySubject(@Nullable String providerId, @Nullable String providerSubject) {
		String key = subjectKey(providerId, providerSubject);
		if (key == null) return CompletableFuture.completedFuture(List.of());

		return stored.get().subjects().read(key).thenCompose(entry -> resolve(
				stored.get().subjects(),
				key,
				entry.orElse(null),
				session -> key.equals(subjectKey(session.getProviderId(), session.getProviderSubject())),
				true
		));
	}

	/**
	 * Lists stored session ids.
	 */
	public @NotNull CompletableFuture<ReplicationPage> list(int page, int pageSize) {
		return stored.get().records().listKeys(page, pageSize);
	}

	/**
	 * Stores a session's record and lists it under its account and provider subject.
	 */
	public @NotNull CompletableFuture<Void> put(@NotNull Session session) {
		return stored.get().records().put(session.getSessionId(), session)
				.thenCompose(ignored -> index(session));
	}

	/**
	 * Lists a stored session under its account and provider subject again, which also renews both entries.
	 * An entry that expired, or lost the id when its proxy stopped mid-change, comes back this way.
	 */
	public @NotNull CompletableFuture<Void> index(@NotNull Session session) {
		String sessionId = session.getSessionId();
		return change(stored.get().accounts(), accountKey(session.getUniqueId()), ids -> listed(ids, sessionId))
				.thenCompose(ignored -> change(
						stored.get().subjects(),
						subjectKey(session.getProviderId(), session.getProviderSubject()),
						ids -> listed(ids, sessionId)
				));
	}

	/**
	 * Writes a session's record again, which gives it its full lifetime, without touching the index entries.
	 */
	public @NotNull CompletableFuture<Void> touch(@NotNull Session session) {
		return stored.get().records().put(session.getSessionId(), session);
	}

	/**
	 * Removes a record that no index entry lists yet, such as the record of a login that was refused after all.
	 */
	public @NotNull CompletableFuture<Void> discard(@NotNull Session session) {
		return stored.get().records().invalidate(session.getSessionId());
	}

	/**
	 * Removes a session's record and its id from the index entries.
	 */
	public @NotNull CompletableFuture<Void> remove(@NotNull Session session) {
		String sessionId = session.getSessionId();
		return stored.get().records().invalidate(sessionId)
				.thenCompose(ignored -> change(stored.get().accounts(), accountKey(session.getUniqueId()), ids -> ids.remove(sessionId)))
				.thenCompose(ignored -> change(
						stored.get().subjects(),
						subjectKey(session.getProviderId(), session.getProviderSubject()),
						ids -> ids.remove(sessionId)
				));
	}

	/**
	 * Removes a session's id from a provider subject it no longer has.
	 */
	public @NotNull CompletableFuture<Void> unlistSubject(@NotNull Session session, @Nullable String providerId, @Nullable String providerSubject) {
		String sessionId = session.getSessionId();
		return change(stored.get().subjects(), subjectKey(providerId, providerSubject), ids -> ids.remove(sessionId));
	}

	/**
	 * Resolves the ids of an index entry against the records and, when asked, drops the ids whose session is gone or
	 * no longer belongs under the key, unless the entry changed meanwhile.
	 */
	private @NotNull CompletableFuture<List<Session>> resolve(
			@NotNull ReplicatedCache<String> index,
			@NotNull String key,
			@Nullable Versioned<String> entry,
			@NotNull Predicate<Session> belongs,
			boolean prune
	) {
		Set<String> ids = ids(entry != null ? entry.getValue() : null);
		List<Session> sessions = new ArrayList<>();
		Set<String> stale = new LinkedHashSet<>();

		CompletableFuture<Void> lookups = CompletableFuture.completedFuture(null);
		for (String id : ids) {
			lookups = lookups.thenCompose(ignored -> stored.get().records().getFresh(id)).thenAccept(found -> {
				if (found.isPresent() && belongs.test(found.get())) sessions.add(found.get());
				else stale.add(id);
			});
		}

		return lookups
				.thenCompose(ignored -> !prune || stale.isEmpty() || entry == null
						? CompletableFuture.completedFuture(true)
						: prune(index, key, entry, ids, stale))
				.thenApply(ignored -> {
					sessions.sort(OLDEST_FIRST);
					return sessions;
				});
	}

	private @NotNull CompletableFuture<Boolean> prune(
			@NotNull ReplicatedCache<String> index,
			@NotNull String key,
			@NotNull Versioned<String> entry,
			@NotNull Set<String> ids,
			@NotNull Set<String> stale
	) {
		Set<String> kept = new LinkedHashSet<>(ids);
		kept.removeAll(stale);

		return kept.isEmpty()
				? index.remove(key, entry)
				: index.write(key, entry, String.join(SEPARATOR, kept));
	}

	/**
	 * Reads an index entry, applies a change to its ids and, when the change asks for it, writes the entry back or
	 * removes it once it is empty. The write only succeeds while the entry is still as read; when another proxy
	 * changed it first, the change is applied again to what that proxy wrote, a bounded number of times.
	 */
	private @NotNull CompletableFuture<Void> change(
			@NotNull ReplicatedCache<String> index,
			@Nullable String key,
			@NotNull Predicate<Set<String>> change
	) {
		if (key == null) return CompletableFuture.completedFuture(null);

		return change(index, key, change, 1);
	}

	private @NotNull CompletableFuture<Void> change(
			@NotNull ReplicatedCache<String> index,
			@NotNull String key,
			@NotNull Predicate<Set<String>> change,
			int attempt
	) {
		return index.read(key).thenCompose(entry -> {
			Set<String> ids = ids(entry.map(Versioned::getValue).orElse(null));
			if (!change.test(ids)) return CompletableFuture.completedFuture(true);
			if (ids.isEmpty())
				return entry.isPresent() ? index.remove(key, entry.get()) : CompletableFuture.completedFuture(true);

			return index.write(key, entry.orElse(null), String.join(SEPARATOR, ids));
		}).thenCompose(written -> {
			if (written) return CompletableFuture.<Void>completedFuture(null);
			if (attempt < CHANGE_ATTEMPTS) return change(index, key, change, attempt + 1);

			Logger.warn("Gave up changing session index %s after %s attempts: it kept changing", key, CHANGE_ATTEMPTS);
			return CompletableFuture.<Void>completedFuture(null);
		});
	}

	/** Adds an id and always asks for the write, which renews the entry. */
	private static boolean listed(@NotNull Set<String> ids, @NotNull String sessionId) {
		ids.add(sessionId);
		return true;
	}

	private static @NotNull Set<String> ids(@Nullable String entry) {
		Set<String> ids = new LinkedHashSet<>();
		if (entry == null) return ids;

		for (String id : entry.split(SEPARATOR))
			if (!id.isBlank()) ids.add(id);
		return ids;
	}

	private static @Nullable String accountKey(@Nullable UUID uniqueId) {
		return uniqueId != null ? uniqueId.toString() : null;
	}

	private static @Nullable String subjectKey(@Nullable String providerId, @Nullable String providerSubject) {
		String provider = trimToNull(providerId);
		String subject = trimToNull(providerSubject);
		if (provider == null || subject == null) return null;

		return provider.toLowerCase() + ":" + subject.toLowerCase();
	}

	private static @Nullable String trimToNull(@Nullable String value) {
		if (value == null) return null;

		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static @NotNull String namespace(@Nullable String namespace, @NotNull String name) {
		if (namespace == null || namespace.isBlank())
			throw new IllegalStateException("replication.cache.sessions." + name + " is missing");

		return namespace;
	}

	/**
	 * The sessions of an account and the index entry they were read from, or {@code null} when there was none.
	 */
	public record AccountSessions(@NotNull List<Session> sessions, @Nullable Versioned<String> entry) {
	}

	/**
	 * What the store is built from, created on first use, once the replication document is loaded: the caches
	 * holding the records and the index entries, with the lease lifetime as the records' time to live.
	 */
	private record Stored(
			@NotNull ReplicatedCache<Session> records,
			@NotNull ReplicatedCache<String> accounts,
			@NotNull ReplicatedCache<String> subjects
	) {
		static @NotNull Stored of(long lifetimeMs, @NotNull Replication replication, @NotNull ReplicationSystem replicationSystem) {
			Replication.Sessions namespaces = replication.getCache().getSessions();
			ReplicationType<String, String> index = ReplicationType.identity(String.class).withCodec(SnapshotCodec.string());

			return new Stored(
					replicationSystem.cache(namespace(namespaces.getRecords(), "records"))
							.defaultTtl(lifetimeMs)
							.replicated(ReplicationType.identity(Session.class)),
					replicationSystem.cache(namespace(namespaces.getAccounts(), "accounts"))
							.defaultTtl(lifetimeMs * INDEX_LIFETIMES)
							.replicated(index),
					replicationSystem.cache(namespace(namespaces.getSubjects(), "subjects"))
							.defaultTtl(lifetimeMs * INDEX_LIFETIMES)
							.replicated(index)
			);
		}
	}
}
