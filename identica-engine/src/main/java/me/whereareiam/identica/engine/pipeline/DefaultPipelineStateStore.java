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
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.StoredPipelineState;
import me.whereareiam.identica.pipeline.state.exception.UnreadablePipelineStateException;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Files each pipeline state under the connection that made it, its {@link PipelineStateReference}. One connection
 * has one state, and no other connection reaches it: a client with the same username from another address reads,
 * consumes and saves a state of its own.
 */
@Singleton
public class DefaultPipelineStateStore implements PipelineStateStore {
	private static final String STATE_NAMESPACE_SUFFIX = ":connections";

	private final Supplier<ReplicatedCache<PipelineStateRecord>> records;

	@Inject
	public DefaultPipelineStateStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider
	) {
		this.records = Suppliers.memoize(() -> replicationSystem
				.cache(resolveNamespace(replicationProvider) + STATE_NAMESPACE_SUFFIX)
				.replicated(ReplicationType.identity(PipelineStateRecord.class)));
	}

	@Override
	public @NotNull Optional<PipelineState> find(@NotNull PipelineStateReference reference) {
		return read(reference, false);
	}

	@Override
	public @NotNull PipelineState load(@NotNull PipelineStateReference reference) {
		return find(reference).orElseGet(PipelineState::initial);
	}

	@Override
	public void save(@NotNull PipelineStateReference reference, @NotNull PipelineState state, long ttlMs) {
		if (ttlMs <= 0 || reference.isEmpty()) return;

		long expiresAt = System.currentTimeMillis() + ttlMs;
		StoredPipelineState stored = state.store();
		records.get().put(reference.key(), new PipelineStateRecord(stored, expiresAt), ttlMs).join();
		EventUtil.callEvent(new PipelineStateSavedEvent(reference, PipelineState.restore(stored), expiresAt));
	}

	@Override
	public @NotNull Optional<PipelineState> consume(@NotNull PipelineStateReference reference) {
		Optional<PipelineState> consumed = read(reference, true);
		consumed.ifPresent(state -> EventUtil.callEvent(new PipelineStateClearedEvent(reference, state)));
		return consumed;
	}

	@Override
	public void clear(@NotNull PipelineStateReference reference) {
		consume(reference);
	}

	private @NotNull Optional<PipelineState> read(@NotNull PipelineStateReference reference, boolean consume) {
		if (reference.isEmpty()) return Optional.empty();

		String key = reference.key();
		PipelineStateRecord record = records.get().getFresh(key).join().orElse(null);
		if (record == null) {
			Logger.debug("Pipeline state %s miss key=%s", consume ? "consume" : "find", key);
			return Optional.empty();
		}

		if (consume || record.isExpired()) records.get().invalidate(key).join();
		if (record.isExpired()) return Optional.empty();

		PipelineState state = restore(key, record);
		Logger.debug(
				"Pipeline state %s hit key=%s pipeline=%s expiresAt=%s",
				consume ? "consume" : "find",
				key,
				state != null ? state.getPipelineType() : null,
				record.expiresAt
		);
		return Optional.ofNullable(state);
	}

	/**
	 * Restores the state of a record, reading its scenario and journey position right away: most readers start
	 * there, so a state that cannot be read back is discarded here instead of failing each of them until it expires.
	 */
	private @Nullable PipelineState restore(@NotNull String key, @NotNull PipelineStateRecord record) {
		if (record.stored == null) return null;

		PipelineState state = PipelineState.restore(record.stored);
		try {
			state.getScenario();
			state.get(JourneyStateItem.KEY);

			return state;
		} catch (UnreadablePipelineStateException e) {
			Logger.warn("Discarded a saved pipeline state that cannot be read back: %s", e.getMessage());
			records.get().invalidate(key).join();

			return null;
		}
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
		public @Nullable StoredPipelineState stored;
		public long expiresAt;

		private boolean isExpired() {
			return expiresAt > 0 && expiresAt <= System.currentTimeMillis();
		}
	}
}
