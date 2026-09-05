package me.whereareiam.identica.feature.recognition.eligibility;

import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityDecision;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves whether recognition may be attempted for a connection context.
 *
 * <p>This check runs before any stored recognition snapshot is matched.
 * It allows core and addon rules to block or restore recognition eligibility
 * for reconnect and provider-scoped handshake recognition paths.</p>
 *
 * <pre>{@code
 * RecognitionEligibilityDecision decision = service.evaluate(
 *         RecognitionEligibilityContext.builder()
 *                 .providerId("premium")
 *                 .attemptKind(RecognitionAttemptKind.SESSION_RECOGNITION)
 *                 .trigger(RecognitionTrigger.AUTOMATIC)
 *                 .build()
 * );
 * if (!decision.isAllowed()) {
 *     return false;
 * }
 * }</pre>
 */
public interface RecognitionEligibilityService {
	/**
	 * Evaluates whether recognition may proceed for the given context.
	 *
	 * @param context recognition attempt context
	 * @return resolved eligibility decision
	 */
	@NotNull RecognitionEligibilityDecision evaluate(@NotNull RecognitionEligibilityContext context);
}
