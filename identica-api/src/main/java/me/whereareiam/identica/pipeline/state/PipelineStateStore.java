package me.whereareiam.identica.pipeline.state;

import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Store for pipeline state snapshots.
 */
@SuppressWarnings("unused")
public interface PipelineStateStore {
	/**
	 * Finds a pipeline state snapshot for the reference.
	 *
	 * @param reference state reference
	 * @return optional state snapshot
	 */
	@NotNull Optional<PipelineState> find(@NotNull PipelineStateReference reference);

	/**
	 * Loads a pipeline state snapshot for the reference.
	 *
	 * @param reference state reference
	 * @return state snapshot, or an empty state when missing
	 */
	@NotNull PipelineState load(@NotNull PipelineStateReference reference);

	/**
	 * Saves a pipeline state snapshot with a time-to-live. Only values of {@link StateKey#persisted persisted} keys
	 * are saved.
	 *
	 * <p>Every save is conditional, and the check and the write are one atomic step. A state that was read from this
	 * store is saved only while the stored state is still the one it was read from, which
	 * {@link PipelineState#getRevision()} identifies. A state without a revision, such as
	 * {@link PipelineState#initial()} or a {@link #consume consumed} state, is saved only while no state is stored for
	 * the connection, so of two writers that both found nothing only one creates a state. A writer that means to
	 * replace a state it did not read first reads its revision, as a fresh pipeline run does when it starts. After a
	 * successful save the state carries the new revision, so it can be saved again.</p>
	 *
	 * <pre>{@code
	 * PipelineState state = store.find(reference).orElseGet(PipelineState::initial);
	 * state.put(MigrationPendingState.KEY, pending, ttlMs);
	 * if (!store.save(reference, state, ttlMs)) {
	 *     // another proxy changed the connection's state since it was read; nothing was saved
	 * }
	 * }</pre>
	 *
	 * @param reference state reference
	 * @param state pipeline state
	 * @param ttlMs time-to-live in milliseconds
	 * @return {@code true} when saved, {@code false} when the stored state changed or ended since it was read, or a
	 * state without a revision found one stored; the state is then not saved
	 */
	boolean save(@NotNull PipelineStateReference reference, @NotNull PipelineState state, long ttlMs);

	/**
	 * Consumes a pipeline state snapshot for the reference: removes it only while it is still the state that was
	 * read, so of two consumers only one receives it. The consumed state carries no revision, since nothing is stored
	 * any more.
	 *
	 * @param reference state reference
	 * @return optional state snapshot
	 */
	@NotNull Optional<PipelineState> consume(@NotNull PipelineStateReference reference);

	/**
	 * Clears a pipeline state snapshot for the reference, whatever it holds.
	 *
	 * @param reference state reference
	 */
	void clear(@NotNull PipelineStateReference reference);

	/**
	 * Clears the stored state only while it is still the one a state was read from. A state without a revision
	 * clears nothing: whatever is stored was saved by another writer.
	 *
	 * @param reference state reference
	 * @param read the state as its writer read it
	 * @return {@code true} when nothing newer is stored afterwards, {@code false} when another writer saved a newer
	 * state, which is kept
	 */
	boolean clear(@NotNull PipelineStateReference reference, @NotNull PipelineState read);

	/**
	 * Finds a pipeline state snapshot by resume request.
	 *
	 * @param request resume request
	 * @return optional state snapshot
	 */
	default @NotNull Optional<PipelineState> find(@NotNull ResumeRequest request) {
		return find(PipelineStateReference.from(request));
	}

	/**
	 * Finds a pipeline state snapshot by advance request.
	 *
	 * @param request advance request
	 * @return optional state snapshot
	 */
	default @NotNull Optional<PipelineState> find(@NotNull AdvanceRequest request) {
		return find(PipelineStateReference.from(request));
	}

	/**
	 * Consumes a pipeline state snapshot by resume request.
	 *
	 * @param request resume request
	 * @return optional state snapshot
	 */
	default @NotNull Optional<PipelineState> consume(@NotNull ResumeRequest request) {
		return consume(PipelineStateReference.from(request));
	}

	/**
	 * Returns whether a pipeline state snapshot exists for the resume request.
	 *
	 * @param request resume request
	 * @return {@code true} when a snapshot exists
	 */
	default boolean hasState(@NotNull ResumeRequest request) {
		return find(request).isPresent();
	}

	/**
	 * Returns whether a pipeline state snapshot exists for the advance request.
	 *
	 * @param request advance request
	 * @return {@code true} when a snapshot exists
	 */
	default boolean hasState(@NotNull AdvanceRequest request) {
		return find(request).isPresent();
	}

	/**
	 * Returns whether a pipeline state snapshot exists for the scenario context.
	 *
	 * @param context scenario context
	 * @return {@code true} when a snapshot exists
	 */
	default boolean hasState(@NotNull ScenarioContext context) {
		return find(PipelineStateReference.from(context)).isPresent();
	}
}
