package me.whereareiam.identica.feature.recognition.eligibility;

import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import org.jetbrains.annotations.NotNull;

/**
 * Rule that contributes to recognition eligibility evaluation.
 *
 * <p>Rules run in ascending {@link #order()} and may abstain, allow, or block
 * recognition for the current context.</p>
 *
 * <pre>{@code
 * public final class CorporateRule implements RecognitionEligibilityRule {
 *     public @NotNull String id() {
 *         return "corporate";
 *     }
 *
 *     public int order() {
 *         return 50;
 *     }
 *
 *     public @NotNull RecognitionEligibilityRuleDecision evaluate(
 *             @NotNull RecognitionEligibilityContext context
 *     ) {
 *         return RecognitionEligibilityRuleDecision.abstain();
 *     }
 * }
 * }</pre>
 */
public interface RecognitionEligibilityRule {
	/**
	 * Stable rule identifier used for registration and replacement.
	 *
	 * @return stable rule id
	 */
	@NotNull String id();

	/**
	 * Evaluation order. Lower values run first.
	 *
	 * @return rule order
	 */
	int order();

	/**
	 * Optional guard for context-specific rules.
	 *
	 * @param context recognition attempt context
	 * @return {@code true} when this rule should evaluate
	 */
	default boolean supports(@NotNull RecognitionEligibilityContext context) {
		return true;
	}

	/**
	 * Evaluates this rule for the given context.
	 *
	 * @param context recognition attempt context
	 * @return per-rule decision
	 */
	@NotNull RecognitionEligibilityRuleDecision evaluate(@NotNull RecognitionEligibilityContext context);
}
