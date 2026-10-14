package me.whereareiam.identica.identity.session.exception;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fails a login whose session could not be opened because another login of the same account, typically on another
 * proxy, changed the account's sessions between the moment this login read them and the moment it wrote its own.
 * Nothing of the failed login is kept. A pipeline that meets it disconnects the connection with
 * {@code engine.concurrentChangeKick}, so the player logs in again on what the other login left.
 *
 * <pre>{@code
 * sessionService.open(session).exceptionally(error -> {
 *     if (error.getCause() instanceof ConcurrentSessionChangeException) {
 *         // ask the player to reconnect
 *     }
 *     return null;
 * });
 * }</pre>
 */
@Getter
public class ConcurrentSessionChangeException extends RuntimeException {
	/**
	 * Unique id of the account whose sessions changed.
	 */
	private final @Nullable UUID accountUniqueId;

	/**
	 * Creates the exception for an account whose sessions another login changed.
	 *
	 * @param accountUniqueId unique id of the account
	 */
	public ConcurrentSessionChangeException(@Nullable UUID accountUniqueId) {
		super("The sessions of account " + accountUniqueId + " changed while a login was opening one");
		this.accountUniqueId = accountUniqueId;
	}
}
