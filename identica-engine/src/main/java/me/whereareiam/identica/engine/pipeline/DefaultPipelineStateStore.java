package me.whereareiam.identica.engine.pipeline;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.event.pipeline.state.PipelineStateClearedEvent;
import me.whereareiam.identica.event.pipeline.state.PipelineStateSavedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.StoredPipelineState;
import me.whereareiam.identica.pipeline.state.exception.UnreadablePipelineStateException;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.identica.util.EventUtil;
import me.whereareiam.identica.util.UniqueIdUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultPipelineStateStore implements PipelineStateStore {
	private static final String KEY_CONNECTION_ID_PREFIX = "c:";
	private static final String KEY_IDENTITY_ID_PREFIX = "i:";
	private static final String KEY_CONNECTION_KEY_PREFIX = "k:";
	private static final String STATE_NAMESPACE_SUFFIX = ":records";
	private static final String ALIAS_NAMESPACE_SUFFIX = ":aliases";

	private final Supplier<Caches> caches;

	@Inject
	public DefaultPipelineStateStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider
	) {
		this.caches = Suppliers.memoize(() -> {
			String namespace = resolveNamespace(replicationProvider);
			ReplicationType<String, String> aliasType = ReplicationType.identity(String.class)
					.withCodec(SnapshotCodec.string());
			return new Caches(
					replicationSystem.cache(namespace + STATE_NAMESPACE_SUFFIX).replicated(ReplicationType.identity(PipelineStateRecord.class)),
					replicationSystem.cache(namespace + ALIAS_NAMESPACE_SUFFIX).replicated(aliasType)
			);
		});
	}

	@Override
	public @NotNull Optional<PipelineState> find(@NotNull PipelineStateReference reference) {
		return read(reference, false).state();
	}

	@Override
	public @NotNull PipelineState load(@NotNull PipelineStateReference reference) {
		return find(reference).orElseGet(PipelineState::initial);
	}

	@Override
	public void save(@NotNull PipelineStateReference reference, @NotNull PipelineState state, long ttlMs) {
		if (ttlMs <= 0 || reference.isEmpty()) return;

		List<String> aliases = resolveAliases(reference);
		if (aliases.isEmpty()) return;

		long now = System.currentTimeMillis();
		PipelineStateRecord existing = resolvePrimaryRecord(aliases, now);
		long expiresAt = now + ttlMs;
		StoredPipelineState stored = state.store();
		PipelineStateRecord record = new PipelineStateRecord(
				existing != null && !existing.id.isBlank() ? existing.id : UUID.randomUUID().toString(),
				stored,
				aliases,
				expiresAt
		);

		replaceAliases(record, existing, ttlMs);
		EventUtil.callEvent(new PipelineStateSavedEvent(reference, PipelineState.restore(stored), expiresAt));
	}

	@Override
	public @NotNull Optional<PipelineState> consume(@NotNull PipelineStateReference reference) {
		ReadResult resolved = read(reference, true);
		resolved.state().ifPresent(state -> EventUtil.callEvent(new PipelineStateClearedEvent(reference, state)));
		return resolved.state();
	}

	@Override
	public void clear(@NotNull PipelineStateReference reference) {
		ReadResult resolved = read(reference, true);
		resolved.state().ifPresent(state -> EventUtil.callEvent(new PipelineStateClearedEvent(reference, state)));
	}

	private @NotNull ReadResult read(
			@NotNull PipelineStateReference reference,
			boolean consume
	) {
		if (reference.isEmpty()) return ReadResult.empty();

		List<String> aliases = resolveAliases(reference);
		if (aliases.isEmpty()) return ReadResult.empty();

		long now = System.currentTimeMillis();
		for (String alias : aliases) {
			PipelineStateRecord record = findRecord(alias, now).orElse(null);
			if (record == null) continue;

			PipelineState resolved = restore(record);
			if (consume) invalidateRecord(record);

			Logger.debug(
					"Pipeline state %s hit alias=%s aliases=%s pipeline=%s expiresAt=%s stateId=%s",
					consume ? "consume" : "find",
					alias,
					aliases,
					resolved != null ? resolved.getPipelineType() : null,
					record.expiresAt,
					record.id
			);
			return new ReadResult(record.id, Optional.ofNullable(resolved));
		}

		Logger.debug(
				"Pipeline state %s miss connection=%s identity=%s key=%s aliases=%s",
				consume ? "consume" : "find",
				reference.getConnectionUniqueId(),
				reference.getAccountUniqueId(),
				reference.getConnectionKey(),
				aliases
		);
		return ReadResult.empty();
	}

	private void replaceAliases(
			@NotNull PipelineStateRecord next,
			@Nullable PipelineStateRecord existing,
			long ttlMs
	) {
		if (next.aliases == null) return;
		if (existing != null && existing.aliases != null) {
			for (String alias : existing.aliases) {
				if (next.aliases.contains(alias))
					continue;
				caches.get().aliasCache().invalidate(alias).join();
			}
		}

		caches.get().stateCache().put(next.id, next, ttlMs).join();
		for (String alias : next.aliases)
			caches.get().aliasCache().put(alias, next.id, ttlMs).join();
	}

	private @Nullable PipelineStateRecord resolvePrimaryRecord(@NotNull List<String> aliases, long now) {
		PipelineStateRecord primary = null;
		for (String alias : aliases) {
			PipelineStateRecord found = findRecord(alias, now).orElse(null);
			if (found == null) continue;

			if (primary == null || primary.id.equals(found.id)) {
				primary = found;
				continue;
			}

			PipelineState restored = restore(found);
			invalidateRecord(found);
			if (restored != null)
				EventUtil.callEvent(new PipelineStateClearedEvent(referenceFrom(found, restored), restored));
		}

		return primary;
	}

	private @NotNull Optional<PipelineStateRecord> findRecord(@NotNull String alias, long now) {
		String stateId = caches.get().aliasCache().getFresh(alias).join().orElse(null);
		if (stateId == null) return Optional.empty();

		PipelineStateRecord record = readRecord(stateId);
		if (record == null) {
			caches.get().aliasCache().invalidate(alias).join();
			return Optional.empty();
		}

		if (record.expiresAt > 0 && record.expiresAt <= now) {
			invalidateRecord(record);
			return Optional.empty();
		}

		List<String> recordAliases = record.aliases;
		if (recordAliases == null || !recordAliases.contains(alias)) {
			caches.get().aliasCache().invalidate(alias).join();
			return Optional.empty();
		}

		return Optional.of(record);
	}

	private @Nullable PipelineStateRecord readRecord(@Nullable String stateId) {
		if (stateId == null || stateId.isBlank()) return null;
		return caches.get().stateCache().getFresh(stateId).join().orElse(null);
	}

	private void invalidateRecord(@NotNull PipelineStateRecord record) {
		caches.get().stateCache().invalidate(record.id).join();
		if (record.aliases == null || record.aliases.isEmpty()) return;

		for (String alias : record.aliases)
			caches.get().aliasCache().invalidate(alias).join();
	}

	private @NotNull PipelineStateReference referenceFrom(
			@NotNull PipelineStateRecord record,
			@NotNull PipelineState state
	) {
		ScenarioContext context = state.getScenario();
		if (context != null) return PipelineStateReference.from(context);

		if (record.aliases == null || record.aliases.isEmpty())
			return PipelineStateReference.builder().build();

		PipelineStateReference.PipelineStateReferenceBuilder builder = PipelineStateReference.builder();
		for (String alias : record.aliases) {
			if (alias == null || alias.length() < 3) continue;

			if (alias.startsWith(KEY_CONNECTION_ID_PREFIX)) {
				UniqueIdUtil.parseOptionalUniqueId(alias.substring(KEY_CONNECTION_ID_PREFIX.length()))
						.ifPresent(builder::connectionUniqueId);
				continue;
			}

			if (alias.startsWith(KEY_IDENTITY_ID_PREFIX)) {
				UniqueIdUtil.parseOptionalUniqueId(alias.substring(KEY_IDENTITY_ID_PREFIX.length()))
						.ifPresent(builder::accountUniqueId);
				continue;
			}

			if (alias.startsWith(KEY_CONNECTION_KEY_PREFIX))
				builder.connectionKey(alias.substring(KEY_CONNECTION_KEY_PREFIX.length()));
		}

		return builder.build();
	}

	/**
	 * Restores the state of a record, reading its scenario and journey position right away: most readers start
	 * there, so a state that cannot be read back is discarded here instead of failing each of them until it expires.
	 */
	private @Nullable PipelineState restore(@NotNull PipelineStateRecord record) {
		if (record.stored == null) return null;

		PipelineState state = PipelineState.restore(record.stored);
		try {
			state.getScenario();
			state.get(JourneyStateItem.KEY);

			return state;
		} catch (UnreadablePipelineStateException e) {
			Logger.warn("Discarded a saved pipeline state that cannot be read back: %s", e.getMessage());
			invalidateRecord(record);

			return null;
		}
	}

	private @NotNull List<String> resolveAliases(@NotNull PipelineStateReference reference) {
		List<String> aliases = new ArrayList<>(4);

		if (reference.getConnectionUniqueId() != null)
			aliases.add(KEY_CONNECTION_ID_PREFIX + reference.getConnectionUniqueId());

		if (reference.getAccountUniqueId() != null)
			aliases.add(KEY_IDENTITY_ID_PREFIX + reference.getAccountUniqueId());

		String connectionKey = reference.getConnectionKey();
		if (connectionKey != null && !connectionKey.isBlank()) {
			aliases.add(KEY_CONNECTION_KEY_PREFIX + connectionKey);
			String originAgnosticConnectionKey = resolveOriginAgnosticConnectionKey(connectionKey);
			if (originAgnosticConnectionKey != null && !originAgnosticConnectionKey.equals(connectionKey))
				aliases.add(KEY_CONNECTION_KEY_PREFIX + originAgnosticConnectionKey);
		}

		return aliases;
	}

	private @Nullable String resolveOriginAgnosticConnectionKey(@NotNull String connectionKey) {
		String[] parts = connectionKey.split("\\|", -1);
		if (parts.length != 4)
			return null;

		if (parts[2].isBlank() && parts[3].isBlank())
			return connectionKey;

		return String.join("|", parts[0], parts[1], "", "");
	}

	private static @NotNull String resolveNamespace(@NotNull Provider<Replication> replicationProvider) {
		Replication replication = replicationProvider.get();
		if (replication == null) throw new IllegalStateException("replication is missing");

		String resolved = replication.getCache().getPipelineState();
		if (resolved.isBlank()) throw new IllegalStateException("replication.cache.pipelineState is missing");

		return resolved;
	}

	@NoArgsConstructor
	@AllArgsConstructor
	public static final class PipelineStateRecord {
		public @NotNull String id = "";
		public @Nullable StoredPipelineState stored;
		public @Nullable List<String> aliases;
		public long expiresAt;
	}

	private record ReadResult(
			@Nullable String stateId,
			@NotNull Optional<PipelineState> state
	) {
		private static @NotNull ReadResult empty() {
			return new ReadResult(null, Optional.empty());
		}
	}

	/**
	 * The caches of the records and of the aliases that find them, created on first use from the replication
	 * document, which is loaded by then.
	 */
	private record Caches(
			@NotNull ReplicatedCache<PipelineStateRecord> stateCache,
			@NotNull ReplicatedCache<String> aliasCache
	) {
	}
}
