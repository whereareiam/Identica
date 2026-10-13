package me.whereareiam.identica.model.identity.username;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import org.jetbrains.annotations.NotNull;

/**
 * Two accounts wanting one username, as an outcome of the rule that applies sees it.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class UsernameConflict {
	/**
	 * The kind of username conflicts, under which their outcomes are registered.
	 */
	public static final ConflictKind<UsernameConflict, UsernameConflictDecision> KIND =
			ConflictKind.of("username", UsernameConflict.class, UsernameConflictDecision.class);

	/**
	 * Both sides and what brought the conflict up.
	 */
	private final @NotNull ConflictSituation<UsernameConflictTrigger> situation;

	/**
	 * The rule that applies.
	 */
	private final @NotNull UsernameConflictRule rule;

	/**
	 * The username the arriving account wants.
	 */
	private final @NotNull String username;

	/**
	 * The username the holder has, which may differ from the wanted one in case.
	 */
	private final @NotNull String holderUsername;

	/**
	 * Whether the holder is online.
	 */
	private final boolean holderOnline;
}
