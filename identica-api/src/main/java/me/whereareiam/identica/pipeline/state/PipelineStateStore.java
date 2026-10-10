package me.whereareiam.identica.pipeline.state;

import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.UnaryOperator;

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
	 * Saves a pipeline state snapshot with a time-to-live. {@link PipelineInputItem Input items} are never saved.
	 *
	 * @param reference state reference
	 * @param state pipeline state
	 * @param ttlMs time-to-live in milliseconds
	 */
	void save(@NotNull PipelineStateReference reference, @NotNull PipelineState state, long ttlMs);

	/**
	 * Consumes a pipeline state snapshot for the reference.
	 *
	 * @param reference state reference
	 * @return optional state snapshot
	 */
	@NotNull Optional<PipelineState> consume(@NotNull PipelineStateReference reference);

	/**
	 * Clears a pipeline state snapshot for the reference.
	 *
	 * @param reference state reference
	 */
	void clear(@NotNull PipelineStateReference reference);

	/**
	 * Updates a pipeline state snapshot using the provided updater.
	 *
	 * @param reference state reference
	 * @param ttlMs time-to-live in milliseconds
	 * @param updater state updater
	 * @return updated state snapshot
	 */
	default @NotNull PipelineState update(
			@NotNull PipelineStateReference reference,
			long ttlMs,
			@NotNull UnaryOperator<PipelineState> updater
	) {
		PipelineState current = load(reference);
		PipelineState updated = updater.apply(current);
		save(reference, updated, ttlMs);
		return updated;
	}


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
