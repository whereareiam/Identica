package me.whereareiam.identica.model.identity.username;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.identity.Account;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Accounts that share a username without regard to case, such as {@code Alice} and {@code alice}.
 *
 * <p>Usernames are unique without regard to case, so a player cannot register a name another account
 * holds. Accounts from before that rule, and accounts of a provider that vouches for its usernames,
 * may still share one; each of them logs in through its own provider link.
 */
@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true)
public class AccountUsernameDuplicate {
	/**
	 * The username the accounts share, in lower case.
	 */
	private final @NotNull String username;
	/**
	 * The accounts, at least two, oldest first.
	 */
	private final @NotNull List<Account> accounts;
}
