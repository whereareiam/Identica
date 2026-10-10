package me.whereareiam.identica.pipeline.state;

import org.jetbrains.annotations.NotNull;

/**
 * A change that a command hands to the pipeline together with a resume or advance request, such as a password
 * or a provider choice. The pipeline applies it to the player's state at the start of the run it triggers, so
 * the run stays the only writer of that state.
 *
 * <pre>{@code
 * connectionCoordinator.advance(AdvanceRequest.builder()
 *         .identity(identity)
 *         .input(state -> state.putItem(new CredentialAuthenticationAttempt(password), ttlMs))
 *         .build());
 * }</pre>
 */
@FunctionalInterface
public interface PipelineInput {
	/**
	 * Applies the input to the state of the run.
	 *
	 * @param state state of the run the request triggers
	 */
	void applyTo(@NotNull PipelineState state);
}
