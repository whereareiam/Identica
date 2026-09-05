package me.whereareiam.identica.common.conflict;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.identica.conflict.ConflictGuard;
import me.whereareiam.identica.conflict.ConflictSubject;
import me.whereareiam.identica.conflict.ConflictType;
import me.whereareiam.identica.conflict.resolver.ConflictResolver;
import me.whereareiam.identica.model.config.provider.Conflicts;
import me.whereareiam.identica.model.conflict.ConflictAttributeKey;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Default Conflict Service")
class DefaultConflictServiceTest {
	private static final ConflictAttributeKey<String> SELECTOR = ConflictAttributeKey.string("selector");

	@DisplayName("Conflict guards run before resolvers and can short-circuit resolution")
	@Test
	void guardRunsBeforeResolver() {
		Conflicts conflicts = new Conflicts();
		conflicts.getRules().put("username", rules(defaultRule(entry("test"))));

		AtomicBoolean resolverCalled = new AtomicBoolean(false);
		ConflictResolver resolver = resolver("test", resolverCalled, ConflictResolution.allow());
		ConflictGuard guard = context -> ConflictResolution.deny("guarded");

		DefaultConflictService service = new DefaultConflictService(() -> conflicts, Set.of(guard));
		service.register(resolver);

		ConflictResolution resolution = service.resolve(context("username"));
		assertEquals(ConflictResolution.Decision.DENY, resolution.getDecision());
		assertFalse(resolverCalled.get());
	}

	@DisplayName("Falls back to the default rule after case-specific resolvers pass")
	@Test
	void fallsBackToDefaultRuleWhenCaseResolversPass() {
		Conflicts conflicts = new Conflicts();
		Conflicts.ConflictRules rules = new Conflicts.ConflictRules();
		rules.setDefaultRule(defaultRule(entry("allow")));

		Conflicts.ConflictRules.ConflictRule caseRule = new Conflicts.ConflictRules.ConflictRule();
		caseRule.setWhen(JsonNodeFactory.instance.objectNode().put("selector", "preferred"));
		caseRule.setResolvers(List.of(entry("pass")));
		rules.setCases(List.of(caseRule));

		conflicts.getRules().put("username", rules);

		AtomicBoolean passCalled = new AtomicBoolean(false);
		AtomicBoolean allowCalled = new AtomicBoolean(false);
		ConflictResolver passResolver = resolver("pass", passCalled, ConflictResolution.pass());
		ConflictResolver allowResolver = resolver("allow", allowCalled, ConflictResolution.allow());

		DefaultConflictService service = new DefaultConflictService(() -> conflicts, Set.of());
		service.register(new TestConflictType());
		service.register(passResolver);
		service.register(allowResolver);

		ConflictContext context = context("username");
		context.putAttribute(SELECTOR, "preferred");

		ConflictResolution resolution = service.resolve(context);
		assertEquals(ConflictResolution.Decision.ALLOW, resolution.getDecision());
		assertTrue(passCalled.get());
		assertTrue(allowCalled.get());
	}

	@DisplayName("The first matching case wins")
	@Test
	void firstMatchingCaseWins() {
		Conflicts conflicts = new Conflicts();
		Conflicts.ConflictRules rules = new Conflicts.ConflictRules();
		rules.setDefaultRule(defaultRule(entry("default")));

		Conflicts.ConflictRules.ConflictRule first = new Conflicts.ConflictRules.ConflictRule();
		first.setWhen(JsonNodeFactory.instance.objectNode().put("selector", "preferred"));
		first.setResolvers(List.of(entry("first")));

		Conflicts.ConflictRules.ConflictRule second = new Conflicts.ConflictRules.ConflictRule();
		second.setWhen(JsonNodeFactory.instance.objectNode().put("selector", "preferred"));
		second.setResolvers(List.of(entry("second")));

		rules.setCases(List.of(first, second));
		conflicts.getRules().put("username", rules);

		AtomicBoolean firstCalled = new AtomicBoolean(false);
		AtomicBoolean secondCalled = new AtomicBoolean(false);
		ConflictResolver firstResolver = resolver("first", firstCalled, ConflictResolution.allow());
		ConflictResolver secondResolver = resolver("second", secondCalled, ConflictResolution.deny("denied"));

		DefaultConflictService service = new DefaultConflictService(() -> conflicts, Set.of());
		service.register(new TestConflictType());
		service.register(firstResolver);
		service.register(secondResolver);

		ConflictContext context = context("username");
		context.putAttribute(SELECTOR, "preferred");

		ConflictResolution resolution = service.resolve(context);
		assertEquals(ConflictResolution.Decision.ALLOW, resolution.getDecision());
		assertTrue(firstCalled.get());
		assertFalse(secondCalled.get());
	}

	@Test
	void usesTypeOwnedRulesUntilACentralOverrideIsConfigured() {
		Conflicts configuration = new Conflicts();
		DefaultConflictService service = new DefaultConflictService(() -> configuration, Set.of());
		@SuppressWarnings("unchecked")
		ConflictType<ConflictSubject> type = org.mockito.Mockito.mock(ConflictType.class);
		org.mockito.Mockito.when(type.getKey()).thenReturn("optional-feature");
		org.mockito.Mockito.when(type.getDefaultRules()).thenReturn(rules(defaultRule(entry("feature-rule"))));
		service.register(type);
		service.register(resolver("feature-rule", new AtomicBoolean(), ConflictResolution.deny("feature")));
		service.register(resolver("override", new AtomicBoolean(), ConflictResolution.allow()));

		assertEquals(ConflictResolution.Decision.DENY, service.resolve(context("optional-feature")).getDecision());
		assertTrue(configuration.getRules().isEmpty());
		configuration.getRules().put("optional-feature", rules(defaultRule(entry("override"))));
		assertEquals(ConflictResolution.Decision.ALLOW, service.resolve(context("optional-feature")).getDecision());
		configuration.getRules().clear();
		service.unregister(type);
		assertNull(service.resolve(context("optional-feature")));
	}

	private Conflicts.ConflictRules rules(Conflicts.ConflictRules.ConflictRule defaultRule) {
		Conflicts.ConflictRules rules = new Conflicts.ConflictRules();
		rules.setDefaultRule(defaultRule);
		return rules;
	}

	private Conflicts.ConflictRules.ConflictRule defaultRule(
			Conflicts.ConflictRules.ConflictRule.ResolverEntry resolverEntry
	) {
		Conflicts.ConflictRules.ConflictRule rule = new Conflicts.ConflictRules.ConflictRule();
		rule.setResolvers(List.of(resolverEntry));
		return rule;
	}

	private Conflicts.ConflictRules.ConflictRule.ResolverEntry entry(String id) {
		Conflicts.ConflictRules.ConflictRule.ResolverEntry entry =
				new Conflicts.ConflictRules.ConflictRule.ResolverEntry();
		entry.setId(id);
		return entry;
	}

	private ConflictContext context(String key) {
		return ConflictContext.builder()
				.key(key)
				.hook("prepare")
				.build();
	}

	private ConflictResolver resolver(String id, AtomicBoolean called, ConflictResolution resolution) {
		return new ConflictResolver() {
			@Override
			public @NotNull String getId() {
				return id;
			}

			@Override
			public @NotNull ConflictResolution resolve(@NotNull ConflictContext context, @NotNull JsonNode params) {
				called.set(true);
				return resolution;
			}
		};
	}

	private static final class TestConflictType implements ConflictType<ConflictSubject> {
		@Override
		public @NotNull String getKey() {
			return "username";
		}

		@Override
		public ConflictContext createContext(@NotNull ConflictSubject subject) {
			return null;
		}

		@Override
		public boolean matchesRule(@NotNull ConflictContext context, @NotNull JsonNode when) {
			String selector = context.getAttribute(SELECTOR);
			return selector != null && selector.equalsIgnoreCase(when.path("selector").asText());
		}
	}
}
