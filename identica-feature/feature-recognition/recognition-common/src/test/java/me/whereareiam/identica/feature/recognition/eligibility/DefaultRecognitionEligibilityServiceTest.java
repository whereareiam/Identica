package me.whereareiam.identica.feature.recognition.eligibility;

import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityDecision;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import me.whereareiam.identica.feature.recognition.type.RecognitionAttemptKind;
import me.whereareiam.identica.feature.recognition.type.RecognitionTrigger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Default Recognition Eligibility Service")
class DefaultRecognitionEligibilityServiceTest {
	@DisplayName("All abstain leaves recognition eligible")
	@Test
	void allAbstainLeavesRecognitionEligible() {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();
		registry.register(rule("a", 10, RecognitionEligibilityRuleDecision.abstain(), true));

		RecognitionEligibilityDecision decision = new DefaultRecognitionEligibilityService(registry).evaluate(context());

		assertTrue(decision.isAllowed());
		assertEquals("no-rule-blocked", decision.getReason());
		assertNull(decision.getRuleId());
	}

	@DisplayName("Later block overrides earlier allow")
	@Test
	void laterBlockOverridesEarlierAllow() {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();
		registry.register(rule("allow", 10, RecognitionEligibilityRuleDecision.allow("allowed"), true));
		registry.register(rule("block", 20, RecognitionEligibilityRuleDecision.block("blocked"), true));

		RecognitionEligibilityDecision decision = new DefaultRecognitionEligibilityService(registry).evaluate(context());

		assertFalse(decision.isAllowed());
		assertEquals("blocked", decision.getReason());
		assertEquals("block", decision.getRuleId());
	}

	@DisplayName("Supports false skips a rule")
	@Test
	void supportsFalseSkipsRule() {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();
		registry.register(rule("block", 10, RecognitionEligibilityRuleDecision.block("blocked"), false));

		RecognitionEligibilityDecision decision = new DefaultRecognitionEligibilityService(registry).evaluate(context());

		assertTrue(decision.isAllowed());
	}

	@DisplayName("Order ties are resolved by normalized id")
	@Test
	void orderTiesAreResolvedByNormalizedId() {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();
		registry.register(rule("z-rule", 10, RecognitionEligibilityRuleDecision.allow("z"), true));
		registry.register(rule("a-rule", 10, RecognitionEligibilityRuleDecision.block("a"), true));

		RecognitionEligibilityDecision decision = new DefaultRecognitionEligibilityService(registry).evaluate(context());

		assertFalse(decision.isAllowed());
		assertEquals("a-rule", decision.getRuleId());
	}

	private @NotNull RecognitionEligibilityContext context() {
		return RecognitionEligibilityContext.builder()
				.providerId("premium")
				.attemptKind(RecognitionAttemptKind.SESSION_RECOGNITION)
				.trigger(RecognitionTrigger.AUTOMATIC)
				.build();
	}

	private @NotNull RecognitionEligibilityRule rule(
			@NotNull String id,
			int order,
			@NotNull RecognitionEligibilityRuleDecision decision,
			boolean supported
	) {
		return new RecognitionEligibilityRule() {
			@Override
			public @NotNull String id() {
				return id;
			}

			@Override
			public int order() {
				return order;
			}

			@Override
			public boolean supports(@NotNull RecognitionEligibilityContext context) {
				return supported;
			}

			@Override
			public @NotNull RecognitionEligibilityRuleDecision evaluate(@NotNull RecognitionEligibilityContext context) {
				return decision;
			}
		};
	}
}
