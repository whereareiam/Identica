package me.whereareiam.identica.common.conflict;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.identica.common.conflict.resolver.defaults.KickActiveConflictResolver;
import me.whereareiam.identica.common.conflict.resolver.defaults.KickBothConflictResolver;
import me.whereareiam.identica.common.conflict.resolver.defaults.KickJoinerConflictResolver;
import me.whereareiam.identica.conflict.ConflictGuard;
import me.whereareiam.identica.conflict.resolver.ConflictResolver;
import me.whereareiam.identica.model.config.Providers;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Conflict Service")
class DefaultConflictServiceTest {
	@DisplayName("Conflict guards run before resolvers and can short-circuit resolution")
	@Test
	void guardRunsBeforeResolver() {
		Providers providers = new Providers();
		providers.getConflicts().put("username", rules(defaultRule(entry("test"))));

		AtomicBoolean resolverCalled = new AtomicBoolean(false);
		ConflictResolver resolver = new ConflictResolver() {
			@Override
			public @NotNull String getId() {
				return "test";
			}

			@Override
			public @NotNull ConflictResolution resolve(@NotNull ConflictContext context, @NotNull JsonNode params) {
				resolverCalled.set(true);
				return ConflictResolution.allow();
			}
		};

		ConflictGuard guard = context -> ConflictResolution.deny("guarded");

		DefaultConflictService service = new DefaultConflictService(
				() -> providers,
				Set.of(guard),
				new KickJoinerConflictResolver(),
				new KickActiveConflictResolver(),
				new KickBothConflictResolver()
		);
		service.register(resolver);

		ConflictResolution resolution = service.resolve(context("username"));

		assertEquals(ConflictResolution.Action.DENY, resolution.getAction());
		assertFalse(resolverCalled.get());
	}

	@DisplayName("Falls back to the default rule after pair-specific resolvers pass")
	@Test
	void fallsBackToDefaultRuleWhenPairResolversPass() {
		Providers providers = new Providers();
		Providers.ConflictRules rules = new Providers.ConflictRules();
		rules.setDefaultRule(defaultRule(entry("allow")));

		Providers.ConflictRule pairRule = new Providers.ConflictRule();
		pairRule.setProviders(List.of("premium", "password"));
		pairRule.setResolvers(List.of(entry("pass")));
		rules.setPairs(List.of(pairRule));

		providers.getConflicts().put("username", rules);

		AtomicBoolean passCalled = new AtomicBoolean(false);
		AtomicBoolean allowCalled = new AtomicBoolean(false);
		ConflictResolver passResolver = new ConflictResolver() {
			@Override
			public @NotNull String getId() {
				return "pass";
			}

			@Override
			public @NotNull ConflictResolution resolve(@NotNull ConflictContext context, @NotNull JsonNode params) {
				passCalled.set(true);
				return ConflictResolution.pass();
			}
		};
		ConflictResolver allowResolver = new ConflictResolver() {
			@Override
			public @NotNull String getId() {
				return "allow";
			}

			@Override
			public @NotNull ConflictResolution resolve(@NotNull ConflictContext context, @NotNull JsonNode params) {
				allowCalled.set(true);
				return ConflictResolution.allow();
			}
		};

		DefaultConflictService service = new DefaultConflictService(
				() -> providers,
				Set.of(),
				new KickJoinerConflictResolver(),
				new KickActiveConflictResolver(),
				new KickBothConflictResolver()
		);
		service.register(passResolver);
		service.register(allowResolver);

		ConflictContext context = context("username")
				.toBuilder()
				.incomingLink(link("premium"))
				.existingLink(link("password"))
				.build();

		ConflictResolution resolution = service.resolve(context);
		assertEquals(ConflictResolution.Action.ALLOW, resolution.getAction());
		assertTrue(passCalled.get());
		assertTrue(allowCalled.get());
	}

	private Providers.ConflictRules rules(Providers.ConflictRule defaultRule) {
		Providers.ConflictRules rules = new Providers.ConflictRules();
		rules.setDefaultRule(defaultRule);
		return rules;
	}

	private Providers.ConflictRule defaultRule(Providers.ResolverEntry resolverEntry) {
		Providers.ConflictRule rule = new Providers.ConflictRule();
		rule.setResolvers(List.of(resolverEntry));
		return rule;
	}

	private Providers.ResolverEntry entry(String id) {
		Providers.ResolverEntry entry = new Providers.ResolverEntry();
		entry.setId(id);
		return entry;
	}

	private ConflictContext context(String key) {
		return ConflictContext.builder()
				.key(key)
				.candidate("Player")
				.build();
	}

	private AccountProviderLink link(String providerId) {
		return AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId(providerId)
				.providerSubject("subject-" + providerId)
				.primaryLink(true)
				.build();
	}
}
