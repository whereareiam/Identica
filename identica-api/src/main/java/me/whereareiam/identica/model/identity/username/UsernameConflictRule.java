package me.whereareiam.identica.model.identity.username;

import lombok.ToString;
import me.whereareiam.identica.model.conflict.ConflictRule;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;

/**
 * A username conflict rule: the situations it applies to and the outcome that settles them.
 */
@ToString(callSuper = true)
public class UsernameConflictRule extends ConflictRule<UsernameConflictTrigger> {
}
