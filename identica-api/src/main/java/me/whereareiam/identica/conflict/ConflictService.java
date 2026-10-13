package me.whereareiam.identica.conflict;

import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.conflict.ConflictRule;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Settles conflicts between two accounts over something only one of them may have, such as a username.
 *
 * <p>Each kind of conflict keeps its rules in its own configuration. A rule names the situations it applies to by
 * the providers of both sides, what they guarantee, what brought the conflict up and how the arriving player
 * reached the proxy, and names the outcome that settles it. The most specific rule that applies wins: one naming
 * provider ids beats one naming guarantees, which beats one naming neither; among equally specific rules the
 * first one in the configuration wins. Outcomes are registered by id for their kind, so an addon can add its
 * own.</p>
 *
 * <pre>{@code
 * conflicts.register(new LongestPlaytimeOutcome());
 * Optional<UsernameConflictRule> rule = conflicts.match(document.getRules(), situation);
 * }</pre>
 */
public interface ConflictService {
	/**
	 * Registers an outcome for its kind, replacing an outcome of the same kind with the same id.
	 *
	 * @param outcome outcome to register
	 */
	void register(@NotNull ConflictOutcome<?, ?, ?> outcome);

	/**
	 * Unregisters an outcome when it is the one registered under its kind and id.
	 *
	 * @param outcome outcome to unregister
	 */
	void unregister(@NotNull ConflictOutcome<?, ?, ?> outcome);

	/**
	 * Finds the outcome registered for a kind under an id.
	 *
	 * @param kind kind of conflict
	 * @param id   id of the outcome, as rules name it
	 * @param <C>  what the outcome settles
	 * @param <D>  what the outcome decides
	 * @return the outcome, or empty when none is registered under the id
	 */
	<C, D> @NotNull Optional<ConflictOutcome<C, D, ?>> findOutcome(@NotNull ConflictKind<C, D> kind, @NotNull String id);

	/**
	 * Returns the outcomes registered for a kind.
	 *
	 * @param kind kind of conflict
	 * @param <C>  what the outcomes settle
	 * @param <D>  what the outcomes decide
	 * @return the outcomes
	 */
	<C, D> @NotNull List<ConflictOutcome<C, D, ?>> outcomes(@NotNull ConflictKind<C, D> kind);

	/**
	 * Finds the rule that settles a situation: the most specific rule that applies to it, and the first one in the
	 * list among equally specific ones.
	 *
	 * @param rules     rules in their configured order
	 * @param situation situation to settle
	 * @param <T>       what brings conflicts of the kind up
	 * @param <R>       rule type of the kind
	 * @return the rule, or empty when no rule applies
	 */
	<T extends Enum<T>, R extends ConflictRule<T>> @NotNull Optional<R> match(
			@NotNull List<R> rules,
			@NotNull ConflictSituation<T> situation
	);
}
