package me.whereareiam.identica.identity.username;

import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import org.jetbrains.annotations.NotNull;

/**
 * Decides the username of an arriving account and settles a conflict with the account that holds it, under the
 * username conflict rules.
 *
 * <p>An account follows the name its provider gives it unless an administrator named it by hand. When another
 * account holds that name, the rule that applies decides: the arriving player is refused, or one of the two
 * accounts gets a renamed name. A renamed account takes its own name back on a later join once nobody holds it.</p>
 *
 * <pre>{@code
 * UsernameAssignment assignment = assigner.assign(arrival);
 * if (assignment.isRefused()) return deny(assignment.getMessage());
 * }</pre>
 */
public interface UsernameAssigner {
	/**
	 * Decides the username of an arriving account without changing any account.
	 *
	 * @param arrival the arriving account
	 * @return the assignment
	 */
	@NotNull UsernameAssignment preview(@NotNull UsernameArrival arrival);

	/**
	 * Decides the username of an arriving account and writes it: the holder's new name and, for an account that is
	 * stored already, the account's own. A new account is created by the caller with the assigned username; when
	 * another account took it in the meantime, the caller asks again.
	 *
	 * @param arrival the arriving account
	 * @return the assignment
	 */
	@NotNull UsernameAssignment assign(@NotNull UsernameArrival arrival);
}
