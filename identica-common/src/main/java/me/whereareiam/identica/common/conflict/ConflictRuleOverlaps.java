package me.whereareiam.identica.common.conflict;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.conflict.ConflictPartyCondition;
import me.whereareiam.identica.model.conflict.ConflictRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Finds equally specific rules that can apply to the same situation but settle it differently. The first of such
 * a pair always wins, so the second one never applies where they meet; an administrator usually meant something
 * else.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConflictRuleOverlaps {
	/**
	 * Finds the overlapping pairs of a list of rules.
	 *
	 * @param rules       rules in their configured order
	 * @param sameOutcome whether two rules settle a conflict the same way
	 * @param <R>         rule type
	 * @return the pairs, by their positions in the list
	 */
	public static <R extends ConflictRule<?>> @NotNull List<Overlap> find(
			@NotNull List<R> rules,
			@NotNull BiPredicate<R, R> sameOutcome
	) {
		List<Overlap> overlaps = new ArrayList<>();
		for (int first = 0; first < rules.size(); first++) {
			for (int second = first + 1; second < rules.size(); second++) {
				R winner = rules.get(first);
				R shadowed = rules.get(second);
				if (!ConflictRuleMatcher.specificity(winner).equals(ConflictRuleMatcher.specificity(shadowed))) continue;
				if (!canMeet(winner, shadowed) || sameOutcome.test(winner, shadowed)) continue;

				overlaps.add(new Overlap(first, second));
			}
		}

		return List.copyOf(overlaps);
	}

	private static boolean canMeet(@NotNull ConflictRule<?> first, @NotNull ConflictRule<?> second) {
		return compatible(first.getTrigger(), second.getTrigger())
				&& compatible(first.getArrivedVia(), second.getArrivedVia())
				&& canMeet(first.getArriving(), second.getArriving())
				&& canMeet(first.getHolder(), second.getHolder());
	}

	/**
	 * Two conditions on a side can meet unless they name different guarantees or providers in common to none.
	 * Provider ids and guarantees are not compared with each other: without the providers' declarations either may
	 * hold for the other.
	 */
	private static boolean canMeet(@NotNull ConflictPartyCondition first, @NotNull ConflictPartyCondition second) {
		if (first.getGuarantees() != null && second.getGuarantees() != null
				&& !first.getGuarantees().equals(second.getGuarantees()))
			return false;
		if (first.getProviders() == null || second.getProviders() == null) return true;

		return first.getProviders().stream()
				.anyMatch(provider -> second.getProviders().stream().anyMatch(provider::equalsIgnoreCase));
	}

	private static boolean compatible(Object first, Object second) {
		return first == null || second == null || Objects.equals(first, second);
	}

	/**
	 * Two rules that can apply to the same situation, the first of which wins there.
	 *
	 * @param first  position of the rule that wins
	 * @param second position of the rule that never applies where they meet
	 */
	public record Overlap(int first, int second) {
	}
}
