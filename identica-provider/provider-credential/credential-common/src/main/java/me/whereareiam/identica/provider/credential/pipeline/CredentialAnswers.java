package me.whereareiam.identica.provider.credential.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * IDs of why Credential's waiting steps ask again, which a waiting step's result names next to its chat message.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CredentialAnswers {
	/**
	 * The password was wrong.
	 */
	public static final String PASSWORD_INVALID = "credential:password-invalid";
	/**
	 * The repeated password differs from the new one, which is forgotten, so the player chooses a new password again.
	 */
	public static final String PASSWORDS_DIFFER = "credential:passwords-differ";
	/**
	 * The player tried to register an account that already exists, so they are asked for its password.
	 */
	public static final String ALREADY_REGISTERED = "credential:already-registered";
	/**
	 * The player repeated a password before choosing one.
	 */
	public static final String NO_NEW_PASSWORD = "credential:no-new-password";
	/**
	 * The new password is shorter than the settings allow.
	 */
	public static final String PASSWORD_TOO_SHORT = "credential:password-too-short";
	/**
	 * The new password is longer than the settings allow.
	 */
	public static final String PASSWORD_TOO_LONG = "credential:password-too-long";
	/**
	 * The new password contains spaces.
	 */
	public static final String PASSWORD_HAS_SPACES = "credential:password-has-spaces";
	/**
	 * The new password has fewer uppercase letters than the settings require.
	 */
	public static final String PASSWORD_MISSING_UPPER = "credential:password-missing-upper";
	/**
	 * The new password has fewer lowercase letters than the settings require.
	 */
	public static final String PASSWORD_MISSING_LOWER = "credential:password-missing-lower";
	/**
	 * The new password has fewer digits than the settings require.
	 */
	public static final String PASSWORD_MISSING_NUMBER = "credential:password-missing-number";
	/**
	 * The new password has fewer special characters than the settings require.
	 */
	public static final String PASSWORD_MISSING_SPECIAL = "credential:password-missing-special";
}
