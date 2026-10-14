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
import me.whereareiam.identica.replication.cache.Versioned;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Files each pipeline state under the connection that made it, its {@link PipelineStateReference}. One connection
 * has one state, and no other connection reaches it: a client with the same username from another address reads,
 * consumes and saves a state of its own.
 *
 * <p>Every record carries a revision that changes with each save. A save, a conditional clear and a consume change
 * the record only while it is still the one that was read, so a writer that read a state another proxy changed
 * since never writes over that change.</p>
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
		if (reference.isEmpty()) return Optional.empty();

		String key = reference.key();
		Versioned<PipelineStateRecord> found = records.get().read(key).join().orElse(null);
		if (found == null) {
			Logger.debug("Pipeline state find miss key=%s", key);
			return Optional.empty();
		}

		PipelineState state = restore(key, found);
		logHit("find", key, state, found.getValue());
		return Optional.ofNullable(state);
	}

	@Override
	public @NotNull PipelineState load(@NotNull PipelineStateReference reference) {
		return find(reference).orElseGet(PipelineState::initial);
	}

	@Override
	public boolean save(@NotNull PipelineStateReference reference, @NotNull PipelineState state, long ttlMs) {
		if (ttlMs <= 0 || reference.isEmpty()) return false;

		String key = reference.key();
		Versioned<PipelineStateRecord> current = records.get().read(key).join().orElse(null);
		if (!isReadAt(state, current)) {
			Logger.debug("Pipeline state save refused key=%s read=%s stored=%s", key, state.getRevision(), revision(current));
			return false;
		}

		long expiresAt = System.currentTimeMillis() + ttlMs;
		StoredPipelineState stored = state.store();
		PipelineStateRecord record = new PipelineStateRecord(stored, expiresAt, UUID.randomUUID().toString());
		if (!records.get().write(key, current, record, ttlMs).join()) {
			Logger.debug("Pipeline state save lost to another writer key=%s read=%s", key, state.getRevision());
			return false;
		}

		state.setRevision(record.revision);
		EventUtil.callEvent(new PipelineStateSavedEvent(reference, PipelineState.restore(stored), expiresAt));
		return true;
	}

	@Override
	public @NotNull Optional<PipelineState> consume(@NotNull PipelineStateReference reference) {
		if (reference.isEmpty()) return Optional.empty();

		String key = reference.key();
		Versioned<PipelineStateRecord> found = records.get().read(key).join().orElse(null);
		if (found == null) {
			Logger.debug("Pipeline state consume miss key=%s", key);
			return Optional.empty();
		}
		if (!records.get().remove(key, found).join()) {
			Logger.debug("Pipeline state consume lost to another writer key=%s", key);
			return Optional.empty();
		}

		PipelineState state = restore(key, found);
		logHit("consume", key, state, found.getValue());
		if (state == null) return Optional.empty();

		state.setRevision(null);
		EventUtil.callEvent(new PipelineStateClearedEvent(reference, state));
		return Optional.of(state);
	}

	@Override
	public void clear(@NotNull PipelineStateReference reference) {
		if (reference.isEmpty()) return;

		String key = reference.key();
		Versioned<PipelineStateRecord> found = records.get().read(key).join().orElse(null);
		if (found == null) return;

		records.get().invalidate(key).join();
		PipelineState state = restore(key, found);
		if (state != null) EventUtil.callEvent(new PipelineStateClearedEvent(reference, state));
	}

	@Override
	public boolean clear(@NotNull PipelineStateReference reference, @NotNull PipelineState read) {
		if (reference.isEmpty()) return true;

		String key = reference.key();
		Versioned<PipelineStateRecord> current = records.get().read(key).join().orElse(null);
		if (current == null) return true;

		if (read.getRevision() == null || !isReadAt(read, current) || !records.get().remove(key, current).join()) {
			Logger.debug("Pipeline state kept key=%s: it was saved again after revision %s", key, read.getRevision());
			return false;
		}

		EventUtil.callEvent(new PipelineStateClearedEvent(reference, read));
		return true;
	}

	/**
	 * Returns whether a state was read from the stored record: at the record's revision, or, for a state without a
	 * revision, while nothing is stored.
	 */
	private static boolean isReadAt(@NotNull PipelineState state, @Nullable Versioned<PipelineStateRecord> current) {
		String read = state.getRevision();
		if (current == null) return read == null;

		return read != null && read.equals(current.getValue().revision);
	}

	private static @Nullable String revision(@Nullable Versioned<PipelineStateRecord> current) {
		return current != null ? current.getValue().revision : null;
	}

	private static void logHit(
			@NotNull String operation,
			@NotNull String key,
			@Nullable PipelineState state,
			@NotNull PipelineStateRecord record
	) {
		Logger.debug(
				"Pipeline state %s hit key=%s pipeline=%s expiresAt=%s revision=%s",
				operation,
				key,
				state != null ? state.getPipelineType() : null,
				record.expiresAt,
				record.revision
		);
	}

	/**
	 * Restores the state of a record at the record's revision, reading its scenario and journey position right away:
	 * most readers start there, so a state that cannot be read back is discarded here instead of failing each of them
	 * until it expires.
	 */
	private @Nullable PipelineState restore(@NotNull String key, @NotNull Versioned<PipelineStateRecord> found) {
		PipelineStateRecord record = found.getValue();
		if (record.stored == null) return null;

		PipelineState state = PipelineState.restore(record.stored);
		state.setRevision(record.revision);
		try {
			state.getScenario();
			state.get(JourneyStateItem.KEY);

			return state;
		} catch (UnreadablePipelineStateException e) {
			Logger.warn("Discarded a saved pipeline state that cannot be read back: %s", e.getMessage());
			records.get().remove(key, found).join();

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
		/**
		 * Changes with every save; a writer changes the record only while it is still at the revision it read.
		 */
		public @Nullable String revision;
	}
}
