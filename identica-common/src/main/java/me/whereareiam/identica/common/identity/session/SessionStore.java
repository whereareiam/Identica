package me.whereareiam.identica.common.identity.session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
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
 * The record is the truth. It lives for the heartbeat timeout and has to be refreshed to stay, so the
 * sessions of a proxy that stopped disappear by themselves. An index entry may be out of date: a lookup
 * resolves every id it lists against the records, returns only the sessions that exist and still belong
 * under that key, and drops the other ids from the entry. Nothing is read from the proxy's local copies
 * while the shared store is available, so every proxy sees a session appear and end at once.
 */
@Singleton
public class SessionStore {
	/** An index entry outlives the records it lists by this factor; it is renewed with {@link #index}. */
	static final int INDEX_LIFETIMES = 10;

	private static final String SEPARATOR = "\n";
	/** Sessions created in the same millisecond keep the order in which the index entry lists them. */
	private static final Comparator<Session> OLDEST_FIRST = Comparator.comparingLong(Session::getCreatedAt);

	private final long lifetimeMs;
	private final ReplicatedCache<Session> records;
	private final ReplicatedCache<String> accounts;
	private final ReplicatedCache<String> subjects;

	@Inject
	public SessionStore(
			Provider<Settings> settingsProvider,
			Provider<Replication> replicationProvider,
			ReplicationSystem replicationSystem
	) {
		this(settingsProvider.get().getSessions().heartbeatTimeoutMillis(), replicationProvider.get(), replicationSystem);
	}

	SessionStore(long lifetimeMs, Replication replication, ReplicationSystem replicationSystem) {
		Replication.Sessions namespaces = replication.getCache().getSessions();
		ReplicationType<String, String> index = ReplicationType.identity(String.class).withCodec(SnapshotCodec.string());

		this.lifetimeMs = lifetimeMs;
		this.records = replicationSystem.cache(namespace(namespaces.getRecords(), "records"))
				.defaultTtl(lifetimeMs)
				.replicated(ReplicationType.identity(Session.class));
		this.accounts = replicationSystem.cache(namespace(namespaces.getAccounts(), "accounts"))
				.defaultTtl(lifetimeMs * INDEX_LIFETIMES)
				.replicated(index);
		this.subjects = replicationSystem.cache(namespace(namespaces.getSubjects(), "subjects"))
				.defaultTtl(lifetimeMs * INDEX_LIFETIMES)
				.replicated(index);
	}

	/**
	 * Returns how long a record stays without being refreshed, in milliseconds.
	 */
	public long lifetimeMs() {
		return lifetimeMs;
	}

	public @NotNull CompletableFuture<Optional<Session>> find(@Nullable String sessionId) {
		String key = trimToNull(sessionId);
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());

		return records.getFresh(key);
	}

	/**
	 * Returns the sessions of an account, oldest first.
	 */
	public @NotNull CompletableFuture<List<Session>> findByAccount(@Nullable UUID uniqueId) {
		String key = accountKey(uniqueId);
		return resolve(accounts, key, session -> key.equals(accountKey(session.getUniqueId())));
	}

	/**
	 * Returns the sessions of a provider subject, oldest first.
	 */
	public @NotNull CompletableFuture<List<Session>> findBySubject(@Nullable String providerId, @Nullable String providerSubject) {
		String key = subjectKey(providerId, providerSubject);
		return resolve(subjects, key, session -> key.equals(subjectKey(session.getProviderId(), session.getProviderSubject())));
	}

	/**
	 * Lists stored session ids.
	 */
	public @NotNull CompletableFuture<ReplicationPage> list(int page, int pageSize) {
		return records.listKeys(page, pageSize);
	}

	/**
	 * Stores a session's record and lists it under its account and provider subject.
	 */
	public @NotNull CompletableFuture<Void> put(@NotNull Session session) {
		return records.put(session.getSessionId(), session)
				.thenCompose(ignored -> index(session));
	}

	/**
	 * Lists a stored session under its account and provider subject again, which also renews both entries.
	 * An entry lost to a concurrent change or to its own expiry comes back this way.
	 */
	public @NotNull CompletableFuture<Void> index(@NotNull Session session) {
		String sessionId = session.getSessionId();
		return change(accounts, accountKey(session.getUniqueId()), ids -> listed(ids, sessionId))
				.thenCompose(ignored -> change(
						subjects,
						subjectKey(session.getProviderId(), session.getProviderSubject()),
						ids -> listed(ids, sessionId)
				));
	}

	/**
	 * Writes a session's record again, which gives it its full lifetime, without touching the index entries.
	 */
	public @NotNull CompletableFuture<Void> touch(@NotNull Session session) {
		return records.put(session.getSessionId(), session);
	}

	/**
	 * Removes a session's record and its id from the index entries.
	 */
	public @NotNull CompletableFuture<Void> remove(@NotNull Session session) {
		String sessionId = session.getSessionId();
		return records.invalidate(sessionId)
				.thenCompose(ignored -> change(accounts, accountKey(session.getUniqueId()), ids -> ids.remove(sessionId)))
				.thenCompose(ignored -> change(
						subjects,
						subjectKey(session.getProviderId(), session.getProviderSubject()),
						ids -> ids.remove(sessionId)
				));
	}

	/**
	 * Removes a session's id from a provider subject it no longer has.
	 */
	public @NotNull CompletableFuture<Void> unlistSubject(@NotNull Session session, @Nullable String providerId, @Nullable String providerSubject) {
		String sessionId = session.getSessionId();
		return change(subjects, subjectKey(providerId, providerSubject), ids -> ids.remove(sessionId));
	}

	private @NotNull CompletableFuture<List<Session>> resolve(
			@NotNull ReplicatedCache<String> index,
			@Nullable String key,
			@NotNull Predicate<Session> belongs
	) {
		if (key == null) return CompletableFuture.completedFuture(List.of());

		return index.getFresh(key).thenCompose(entry -> {
			Set<String> ids = ids(entry.orElse(null));
			List<Session> sessions = new ArrayList<>();
			Set<String> stale = new LinkedHashSet<>();

			CompletableFuture<Void> lookups = CompletableFuture.completedFuture(null);
			for (String id : ids) {
				lookups = lookups.thenCompose(ignored -> records.getFresh(id)).thenAccept(found -> {
					if (found.isPresent() && belongs.test(found.get())) sessions.add(found.get());
					else stale.add(id);
				});
			}

			return lookups
					.thenCompose(ignored -> stale.isEmpty()
							? CompletableFuture.<Void>completedFuture(null)
							: change(index, key, current -> current.removeAll(stale)))
					.thenApply(ignored -> {
						sessions.sort(OLDEST_FIRST);
						return sessions;
					});
		});
	}

	/**
	 * Reads an index entry, applies a change to its ids and, when the change asks for it, writes the entry
	 * back or removes it once it is empty. The read and the write are two operations: two proxies changing one entry at the same moment can
	 * lose one change, which {@link #index} and the validation in {@link #resolve} repair.
	 */
	private @NotNull CompletableFuture<Void> change(
			@NotNull ReplicatedCache<String> index,
			@Nullable String key,
			@NotNull Predicate<Set<String>> change
	) {
		if (key == null) return CompletableFuture.completedFuture(null);

		return index.getFresh(key).thenCompose(entry -> {
			Set<String> ids = ids(entry.orElse(null));
			if (!change.test(ids)) return CompletableFuture.completedFuture(null);

			return ids.isEmpty()
					? index.invalidate(key)
					: index.put(key, String.join(SEPARATOR, ids));
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
}
