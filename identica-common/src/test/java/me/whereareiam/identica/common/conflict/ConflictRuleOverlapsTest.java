package me.whereareiam.identica.common.conflict;

import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.whereareiam.identica.common.conflict.ConflictRules.NONE;
import static me.whereareiam.identica.common.conflict.ConflictRules.OWNED;
import static me.whereareiam.identica.common.conflict.ConflictRules.guarantees;
import static me.whereareiam.identica.common.conflict.ConflictRules.providers;
import static me.whereareiam.identica.common.conflict.ConflictRules.rule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Conflict Rule Overlaps")
class ConflictRuleOverlapsTest {
	@DisplayName("Finds equally specific rules that can meet and settle differently")
	@Test
	void findsOverlappingRules() {
		List<UsernameConflictRule> rules = List.of(
				guarantees(OWNED, null, "rename-holder"),
				guarantees(null, NONE, "refuse"),
				rule("refuse")
		);

		assertEquals(List.of(new ConflictRuleOverlaps.Overlap(0, 1)), find(rules));
	}

	@DisplayName("Leaves rules alone that settle alike, cannot meet, or differ in specificity")
	@Test
	void ignoresRulesThatNeverDisagree() {
		UsernameConflictRule firstJoin = guarantees(OWNED, NONE, "rename-holder");
		firstJoin.setTrigger(UsernameConflictTrigger.FIRST_JOIN);
		UsernameConflictRule rename = guarantees(OWNED, NONE, "rename-arriving");
		rename.setTrigger(UsernameConflictTrigger.PROVIDER_RENAME);

		assertTrue(find(List.of(guarantees(OWNED, null, "refuse"), guarantees(null, NONE, "refuse"))).isEmpty());
		assertTrue(find(List.of(firstJoin, rename)).isEmpty());
		assertTrue(find(List.of(guarantees(OWNED, NONE, "refuse"), guarantees(NONE, NONE, "rename-holder"))).isEmpty());
		assertTrue(find(List.of(providers(List.of("alpha"), null, "refuse"), providers(List.of("beta"), null, "rename-holder"))).isEmpty());
		assertTrue(find(List.of(guarantees(OWNED, NONE, "refuse"), rule("rename-holder"))).isEmpty());
	}

	private static List<ConflictRuleOverlaps.Overlap> find(List<UsernameConflictRule> rules) {
		return ConflictRuleOverlaps.find(rules, (first, second) -> first.getOutcome().equals(second.getOutcome()));
	}
}
