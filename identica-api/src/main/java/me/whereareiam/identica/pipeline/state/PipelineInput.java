package me.whereareiam.identica.pipeline.state;

import org.jetbrains.annotations.NotNull;

/**
 * A change that a command hands to the pipeline together with a resume or advance request, such as a password
 * or a provider choice. The pipeline applies it to the player's state at the start of the run it triggers, so
 * the run stays the only writer of that state. What the input puts there under a {@link StateKey#persisted persisted}
 * key is saved with the state once the run ends; values under a {@link StateKey#run run} key, such as a password,
 * live only in the run.
 *
 * <pre>{@code
 * connectionCoordinator.advance(AdvanceRequest.builder()
 *         .identity(identity)
 *         .input(state -> state.put(CredentialAuthenticationAttempt.KEY, new CredentialAuthenticationAttempt(password)))
 *         .build());
 * }</pre>
 *
 * <p>An input that only fits some pending journeys overrides {@link #accepts(PipelineState)}. The pipeline asks it
 * in the connection's turn, after every earlier run of the connection has finished, so the answer reflects the state
 * the run would start from rather than a state another run is still changing.</p>
 */
@FunctionalInterface
public interface PipelineInput {
	/**
	 * Applies the input to the state of the run.
	 *
	 * @param state state of the run the request triggers
	 */
	void applyTo(@NotNull PipelineState state);

	/**
	 * Decides whether the pending journey takes this input. A declined input starts no run, and the request ends
	 * with {@link me.whereareiam.identica.model.auth.ConnectionDecision.Status#NO_PENDING NO_PENDING}, as when
	 * nothing is pending at all. The pipeline asks before it applies the input, with the state the run would start
	 * from; the input only reads it.
	 *
	 * <pre>{@code
	 * public boolean accepts(PipelineState state) {
	 *     return state.getPipelineType() == PipelineType.AUTHENTICATION;
	 * }
	 * }</pre>
	 *
	 * @param state state of the pending journey, to read only
	 * @return {@code true} when the pending journey takes the input; by default always
	 */
	default boolean accepts(@NotNull PipelineState state) {
		return true;
	}
}
