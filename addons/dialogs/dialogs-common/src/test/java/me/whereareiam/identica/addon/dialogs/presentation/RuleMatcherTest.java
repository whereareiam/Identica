package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleMatcherTest {
	private static final DialogRule EVENT_LOGIN = rule(Map.of("prompt", "credential:password", "host", "event.example.net"), "event-login");
	private static final DialogRule LOGIN = rule(Map.of("prompt", "credential:password"), "login");
	private static final DialogRule WRONG = rule(Map.of("message", "credential:password-invalid"), "wrong");

	@Test
	void picksTheFirstRuleWhoseConditionsMatchIgnoringCase() {
		List<DialogRule> rules = List.of(EVENT_LOGIN, LOGIN, WRONG);

		assertEquals(Optional.of(EVENT_LOGIN), RuleMatcher.first(rules, Map.of("prompt", "CREDENTIAL:password", "host", "Event.Example.Net")));
		assertEquals(Optional.of(LOGIN), RuleMatcher.first(rules, Map.of("prompt", "credential:password", "host", "play.example.net")));
		assertEquals(Optional.of(WRONG), RuleMatcher.first(rules, Map.of("message", "credential:password-invalid")));
	}

	@Test
	void neverMatchesAConditionOnAnAttributeTheMessageDoesNotHave() {
		assertEquals(Optional.empty(), RuleMatcher.first(List.of(EVENT_LOGIN), Map.of("prompt", "credential:password")));
	}

	private static DialogRule rule(Map<String, String> when, String dialog) {
		return DialogRule.builder().when(when).dialog(dialog).build();
	}
}
