package me.whereareiam.identica.feature.verification.model.resolution;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.pipeline.state.StateKey;
import org.jetbrains.annotations.NotNull;

/**
 * The code a player typed for the verification their pending journey waits for. A command hands it to the pipeline
 * as input, and the provider's verification step passes it on with its
 * {@link VerificationResolutionRequest#getCode() resolution request}. It lives only in the run the command starts
 * and is never saved with the pipeline state.
 *
 * <pre>{@code
 * connectionCoordinator.advance(AdvanceRequest.builder()
 *         .identity(identity)
 *         .input(state -> state.put(VerificationCodeAttempt.KEY, new VerificationCodeAttempt(code)))
 *         .build());
 * }</pre>
 */
@Getter
@ToString(exclude = "code")
@RequiredArgsConstructor
public class VerificationCodeAttempt {
	/**
	 * Key of the typed code in a pipeline state. Only lives during one run and is never saved.
	 */
	public static final StateKey<VerificationCodeAttempt> KEY =
			StateKey.run("verification:code-attempt", VerificationCodeAttempt.class);

	private final @NotNull String code;
}
