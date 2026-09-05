package me.whereareiam.identica.feature.recognition.model.eligibility;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decision produced by a single recognition eligibility rule.
 */
@Getter
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor
public class RecognitionEligibilityRuleDecision {
	private final @NotNull Status status;
	private final @Nullable String reason;

	/**
	 * Creates an abstain decision.
	 *
	 * @return abstain decision
	 */
	public static @NotNull RecognitionEligibilityRuleDecision abstain() {
		return new RecognitionEligibilityRuleDecision(Status.ABSTAIN, null);
	}

	/**
	 * Creates an allow decision.
	 *
	 * @param reason optional allow reason
	 * @return allow decision
	 */
	public static @NotNull RecognitionEligibilityRuleDecision allow(@Nullable String reason) {
		return new RecognitionEligibilityRuleDecision(Status.ALLOW, reason);
	}

	/**
	 * Creates a block decision.
	 *
	 * @param reason optional block reason
	 * @return block decision
	 */
	public static @NotNull RecognitionEligibilityRuleDecision block(@Nullable String reason) {
		return new RecognitionEligibilityRuleDecision(Status.BLOCK, reason);
	}

	/**
	 * Per-rule recognition eligibility result.
	 */
	public enum Status {
		ABSTAIN,
		ALLOW,
		BLOCK
	}
}
