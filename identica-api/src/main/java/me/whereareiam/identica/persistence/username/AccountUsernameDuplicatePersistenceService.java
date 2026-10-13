package me.whereareiam.identica.persistence.username;

import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Finds accounts that share a username without regard to case.
 */
public interface AccountUsernameDuplicatePersistenceService {
	/**
	 * Finds every group of accounts that share a username without regard to case.
	 *
	 * @return the groups, ordered by username; empty when every username belongs to one account
	 */
	@NotNull List<AccountUsernameDuplicate> findAll();
}
