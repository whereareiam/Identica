package me.whereareiam.identica.feature.recognition.eligibility;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityDecision;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultRecognitionEligibilityService implements RecognitionEligibilityService {
	private final RecognitionEligibilityRegistry registry;

	@Override
	public @NotNull RecognitionEligibilityDecision evaluate(@NotNull RecognitionEligibilityContext context) {
		RecognitionEligibilityDecision allowed = RecognitionEligibilityDecision.builder()
				.allowed(true)
				.reason("no-rule-blocked")
				.build();

		return registry.resolve().stream()
				.sorted(Comparator.comparingInt(RecognitionEligibilityRule::order)
						.thenComparing(rule -> rule.id().trim().toLowerCase(java.util.Locale.ROOT)))
				.filter(rule -> rule.supports(context))
				.reduce(
						allowed,
						(current, rule) -> evaluateRule(current, rule, context),
						(left, right) -> right
				);
	}

	private @NotNull RecognitionEligibilityDecision evaluateRule(
			@NotNull RecognitionEligibilityDecision current,
			@NotNull RecognitionEligibilityRule rule,
			@NotNull RecognitionEligibilityContext context
	) {
		if (!current.isAllowed()) return current;

		RecognitionEligibilityRuleDecision decision = rule.evaluate(context);
		if (decision.getStatus() == RecognitionEligibilityRuleDecision.Status.ABSTAIN) return current;
		if (decision.getStatus() == RecognitionEligibilityRuleDecision.Status.BLOCK)
			return RecognitionEligibilityDecision.builder()
					.allowed(false)
					.reason(decision.getReason())
					.ruleId(rule.id())
					.build();

		if (current.getRuleId() != null) return current;
		return RecognitionEligibilityDecision.builder()
				.allowed(true)
				.reason(decision.getReason())
				.ruleId(rule.id())
				.build();
	}
}
