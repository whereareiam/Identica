package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleMatcherTest {
	private static final DialogRule EVENT_LOGIN = rule("step-waiting", Map.of("step", "password-authentication-password", "host", "event.example.net"), "event-login");
	private static final DialogRule LOGIN = rule("step-waiting", Map.of("step", "password-authentication-password"), "login");
	private static final DialogRule MENU = rule("command", Map.of(), "menu");

	@Test
	void picksTheFirstRuleWhoseTriggerAndConditionsMatchIgnoringCase() {
		List<DialogRule> rules = List.of(EVENT_LOGIN, LOGIN, MENU);

		assertEquals(Optional.of(EVENT_LOGIN), RuleMatcher.first(rules, context("step-waiting",
				Map.of("step", "PASSWORD-AUTHENTICATION-PASSWORD", "host", "Event.Example.Net"))));
		assertEquals(Optional.of(LOGIN), RuleMatcher.first(rules, context("step-waiting",
				Map.of("step", "password-authentication-password", "host", "play.example.net"))));
		assertEquals(Optional.of(MENU), RuleMatcher.first(rules, context("command", Map.of())));
	}

	@Test
	void neverMatchesAConditionOnAnAttributeTheTriggerDoesNotReport() {
		assertEquals(Optional.empty(), RuleMatcher.first(List.of(EVENT_LOGIN), context("step-waiting",
				Map.of("step", "password-authentication-password"))));
		assertEquals(Optional.empty(), RuleMatcher.first(List.of(LOGIN), context("command",
				Map.of("step", "password-authentication-password"))));
	}

	private static DialogRule rule(String on, Map<String, String> when, String dialog) {
		return DialogRule.builder().on(on).when(when).dialog(dialog).build();
	}

	private static TriggerContext context(String trigger, Map<String, String> attributes) {
		return TriggerContext.builder().connectionUniqueId(UUID.randomUUID()).trigger(trigger).attributes(attributes).build();
	}
}
