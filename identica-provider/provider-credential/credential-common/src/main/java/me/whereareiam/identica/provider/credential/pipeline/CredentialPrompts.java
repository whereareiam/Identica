package me.whereareiam.identica.provider.credential.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * IDs of what Credential's waiting steps ask the player for, which a waiting step's result names next to its chat
 * message.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CredentialPrompts {
	/**
	 * The password of an existing account, to log in or to confirm a move to another provider.
	 */
	public static final String PASSWORD = "credential:password";
	/**
	 * A new password, to register or to move an account to Credential.
	 */
	public static final String NEW_PASSWORD = "credential:new-password";
	/**
	 * The new password once more, to confirm it.
	 */
	public static final String REPEAT_PASSWORD = "credential:repeat-password";
}
