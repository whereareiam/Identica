package me.whereareiam.identica.feature.recognition.eligibility;

import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Default Recognition Eligibility Registry")
class DefaultRecognitionEligibilityRegistryTest {
	@DisplayName("Registers, replaces, and unregisters rules by id")
	@Test
	void registersReplacesAndUnregistersRulesById() {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();

		registry.register(rule("corporate", 10));
		registry.register(rule("Corporate", 20));

		assertEquals(1, registry.resolve().size());
		assertEquals(20, registry.resolve().getFirst().order());
		assertTrue(registry.unregister("CORPORATE"));
		assertTrue(registry.resolve().isEmpty());
	}

	@DisplayName("Resolved rule list is immutable")
	@Test
	void resolvedRuleListIsImmutable() {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();
		registry.register(rule("rule-1", 10));

		assertThrows(UnsupportedOperationException.class, () -> registry.resolve().add(rule("rule-2", 20)));
	}

	private @NotNull RecognitionEligibilityRule rule(@NotNull String id, int order) {
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
			public @NotNull RecognitionEligibilityRuleDecision evaluate(@NotNull RecognitionEligibilityContext context) {
				return RecognitionEligibilityRuleDecision.abstain();
			}
		};
	}
}
