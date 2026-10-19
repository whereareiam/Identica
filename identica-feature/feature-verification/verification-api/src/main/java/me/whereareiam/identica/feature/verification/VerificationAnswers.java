package me.whereareiam.identica.feature.verification;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * IDs of why steps that verify a player ask again, which a waiting step's result names next to its chat message.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerificationAnswers {
	/**
	 * The code was wrong.
	 */
	public static final String CODE_INVALID = "verification:code-invalid";
	/**
	 * No challenge was waiting for the code.
	 */
	public static final String NO_CHALLENGE = "verification:no-challenge";
}
