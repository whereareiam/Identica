package me.whereareiam.identica.conflict;

import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.type.conflict.ConflictSide;
import org.jetbrains.annotations.NotNull;

/**
 * Settles a conflict the way a rule names it. Outcomes never decide that one account gets into another one: they
 * only decide what each side keeps. Register an outcome with {@link ConflictService#register(ConflictOutcome)};
 * rules name it by its id.
 *
 * <p>An outcome has typed settings with defaults. Its kind writes the defaults into its document under
 * {@code outcomes.<id>} once the outcome is registered; an administrator changes them there, per provider, or on
 * one rule, and each place states only what it changes.</p>
 *
 * <pre>{@code
 * public final class LongestPlaytimeOutcome implements ConflictOutcome<UsernameConflict, UsernameConflictDecision, LongestPlaytime> {
 *     public ConflictKind<UsernameConflict, UsernameConflictDecision> kind() {
 *         return UsernameConflict.KIND;
 *     }
 *
 *     public String id() {
 *         return "longest-playtime";
 *     }
 *
 *     public Class<LongestPlaytime> settingsType() {
 *         return LongestPlaytime.class;
 *     }
 *
 *     public LongestPlaytime defaults() {
 *         return new LongestPlaytime();
 *     }
 *
 *     public UsernameConflictDecision decide(UsernameConflict conflict, LongestPlaytime settings) {
 *         return playtime.longer(conflict.getSituation())
 *                 ? UsernameConflictDecision.renameHolder(settings.getHolderRename())
 *                 : UsernameConflictDecision.renameArriving(settings.getRename());
 *     }
 * }
 * }</pre>
 *
 * @param <C> what the outcome settles
 * @param <D> what the outcome decides
 * @param <S> the outcome's settings
 */
public interface ConflictOutcome<C, D, S> {
	/**
	 * Returns the kind of conflict the outcome settles.
	 *
	 * @return kind of conflict
	 */
	@NotNull ConflictKind<C, D> kind();

	/**
	 * Returns the id rules name the outcome by, in lowercase kebab case such as {@code rename-holder}.
	 *
	 * @return outcome id
	 */
	@NotNull String id();

	/**
	 * Returns the type of the outcome's settings, which the documents are read into.
	 *
	 * @return settings type
	 */
	@NotNull Class<S> settingsType();

	/**
	 * Returns the outcome's default settings.
	 *
	 * @return new default settings
	 */
	@NotNull S defaults();

	/**
	 * Returns the side whose account the outcome acts on. Settings an administrator gives the provider of that
	 * account's side apply to the outcome.
	 *
	 * @return the side, {@link ConflictSide#ARRIVING} unless the outcome acts on the holder
	 */
	default @NotNull ConflictSide actsOn() {
		return ConflictSide.ARRIVING;
	}

	/**
	 * Decides a conflict.
	 *
	 * @param conflict conflict to decide
	 * @param settings the outcome's settings for this conflict
	 * @return decision
	 */
	@NotNull D decide(@NotNull C conflict, @NotNull S settings);
}
