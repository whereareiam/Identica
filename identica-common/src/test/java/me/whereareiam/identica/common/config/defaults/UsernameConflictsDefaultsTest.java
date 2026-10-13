package me.whereareiam.identica.common.config.defaults;

import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Username Conflicts Defaults")
class UsernameConflictsDefaultsTest {
	private final UsernameConflicts conflicts = new UsernameConflictsDefaults().supply(new UsernameConflicts());
	private final List<UsernameConflictRule> rules = conflicts.getRules();

	@DisplayName("Write the defaults of every built-in outcome and no provider overrides")
	@Test
	void writeOutcomeDefaults() {
		assertEquals(List.of("rename-holder", "rename-arriving", "refuse"), List.copyOf(conflicts.getOutcomes().keySet()));
		assertEquals("KEEP_PLAYING", conflicts.getOutcomes().get("rename-holder").path("holderOnline").asText());
		assertEquals("{username}_{provider}", conflicts.getOutcomes().get("rename-arriving").path("pattern").asText());
		assertTrue(conflicts.getOutcomes().get("refuse").isEmpty());
		assertTrue(conflicts.getProviders().isEmpty());
	}

	@DisplayName("Name guarantees only, never a provider")
	@Test
	void nameNoProvider() {
		for (UsernameConflictRule rule : rules) {
			assertNull(rule.getArriving().getProviders());
			assertNull(rule.getHolder().getProviders());
		}
	}

	@DisplayName("Give an owned name to its owner on a first join and rename the owner on a provider rename")
	@Test
	void settleOwnedNames() {
		assertRule(rules.get(0), Set.of(ProviderGuarantee.OWNED_USERNAME), Set.of(), UsernameConflictTrigger.FIRST_JOIN, "rename-holder");
		assertRule(rules.get(1), Set.of(ProviderGuarantee.OWNED_USERNAME), Set.of(), UsernameConflictTrigger.PROVIDER_RENAME, "rename-arriving");
	}

	@DisplayName("Refuse a chosen name that is held, and refuse everything else")
	@Test
	void refuseTheRest() {
		assertRule(rules.get(2), Set.of(), null, null, "refuse");
		assertRule(rules.get(3), null, null, null, "refuse");
		assertEquals(4, rules.size());
	}

	private static void assertRule(
			UsernameConflictRule rule,
			Set<ProviderGuarantee> arriving,
			Set<ProviderGuarantee> holder,
			UsernameConflictTrigger trigger,
			String outcome
	) {
		assertEquals(arriving, rule.getArriving().getGuarantees());
		assertEquals(holder, rule.getHolder().getGuarantees());
		assertEquals(trigger, rule.getTrigger());
		assertEquals(outcome, rule.getOutcome().getId());
		assertNull(rule.getOutcome().getWith());
	}
}
