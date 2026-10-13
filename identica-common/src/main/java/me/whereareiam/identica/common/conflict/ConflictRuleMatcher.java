package me.whereareiam.identica.common.conflict;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.conflict.ConflictParty;
import me.whereareiam.identica.model.conflict.ConflictPartyCondition;
import me.whereareiam.identica.model.conflict.ConflictRule;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Tells whether a conflict rule applies to a situation and how specific it is.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConflictRuleMatcher {
	/**
	 * Tells whether a rule applies to a situation.
	 *
	 * @param rule      rule
	 * @param situation situation
	 * @return whether every condition of the rule holds
	 */
	public static boolean applies(@NotNull ConflictRule<?> rule, @NotNull ConflictSituation<?> situation) {
		if (rule.getTrigger() != null && rule.getTrigger() != situation.getTrigger()) return false;
		if (rule.getArrivedVia() != null && rule.getArrivedVia() != situation.getArrivedVia()) return false;

		return accepts(rule.getArriving(), situation.getArriving()) && accepts(rule.getHolder(), situation.getHolder());
	}

	/**
	 * Returns how specific a rule is: rules naming provider ids on more sides come first, then rules naming
	 * guarantees on more sides, then rules with more of the other conditions.
	 *
	 * @param rule rule
	 * @return its specificity
	 */
	public static @NotNull Specificity specificity(@NotNull ConflictRule<?> rule) {
		return new Specificity(
				present(rule.getArriving().getProviders(), rule.getHolder().getProviders()),
				present(rule.getArriving().getGuarantees(), rule.getHolder().getGuarantees()),
				present(rule.getTrigger(), rule.getArrivedVia())
		);
	}

	private static int present(Object... conditions) {
		return (int) Arrays.stream(conditions).filter(Objects::nonNull).count();
	}

	private static boolean accepts(@NotNull ConflictPartyCondition condition, @NotNull ConflictParty party) {
		if (condition.getGuarantees() != null && !condition.getGuarantees().equals(party.getGuarantees())) return false;
		if (condition.getProviders() == null) return true;

		String providerId = party.getProviderId();
		return providerId != null && condition.getProviders().stream().anyMatch(providerId::equalsIgnoreCase);
	}

	/**
	 * How specific a rule is, compared side by side: provider ids, then guarantees, then the other conditions.
	 *
	 * @param providers  number of sides the rule names provider ids for
	 * @param guarantees number of sides the rule names guarantees for
	 * @param conditions number of the rule's other conditions
	 */
	public record Specificity(int providers, int guarantees, int conditions) implements Comparable<Specificity> {
		private static final Comparator<Specificity> ORDER = Comparator.comparingInt(Specificity::providers)
				.thenComparingInt(Specificity::guarantees)
				.thenComparingInt(Specificity::conditions);

		@Override
		public int compareTo(@NotNull Specificity other) {
			return ORDER.compare(this, other);
		}
	}
}
