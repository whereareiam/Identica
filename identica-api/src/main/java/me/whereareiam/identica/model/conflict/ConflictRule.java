package me.whereareiam.identica.model.conflict;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A configured rule of a kind of conflict: the situations it applies to and the outcome that settles them. Each
 * kind extends it with what its outcomes need.
 *
 * <p>A rule naming provider ids on a side is more specific than one naming guarantees there, which is more
 * specific than one naming nothing; a rule that also names a trigger or how the player arrived is more specific
 * than one that does not.</p>
 *
 * @param <T> what brings conflicts of the kind up
 */
@Getter
@Setter
@ToString
public class ConflictRule<T extends Enum<T>> {
	/**
	 * What the rule asks of the arriving side.
	 */
	private @NotNull ConflictPartyCondition arriving = new ConflictPartyCondition();

	/**
	 * What the rule asks of the holding side.
	 */
	private @NotNull ConflictPartyCondition holder = new ConflictPartyCondition();

	/**
	 * What must have brought the conflict up, or {@code null} for anything.
	 */
	private @Nullable T trigger;

	/**
	 * Why the arriving player's provider must have been picked, or {@code null} for any reason.
	 */
	private @Nullable ProviderClaimOrigin arrivedVia;

	/**
	 * The outcome that settles the conflict, with the settings this rule changes.
	 */
	private @NotNull ConflictRuleOutcome outcome = ConflictRuleOutcome.of("");
}
