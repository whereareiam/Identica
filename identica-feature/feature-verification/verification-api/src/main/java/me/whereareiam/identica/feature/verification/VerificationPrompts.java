package me.whereareiam.identica.feature.verification;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * IDs of what steps that verify a player ask for, which a waiting step's result names next to its chat message.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerificationPrompts {
	/**
	 * A code from the player's authenticator app, or a recovery code.
	 */
	public static final String CODE = "verification:code";
}
