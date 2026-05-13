package me.whereareiam.identica.provider.password.cryptography;

import me.whereareiam.identica.provider.password.model.PasswordAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for password provider password hashing and verification.
 */
public interface CryptographyService {
	/**
	 * Hash a plain-text password using the configured hashing algorithm.
	 *
	 * @param password plain-text password
	 * @return candidate or {@code null} when hashing is unavailable
	 */
	@Nullable PasswordCandidate hash(@NotNull String password);

	/**
	 * Verify a plain-text password against the stored account hash and method.
	 *
	 * @param account account to verify
	 * @param password plain-text password
	 * @return {@code true} when the password matches the stored hash
	 * @implNote Successful verification dispatches {@code PasswordVerifiedEvent}.
	 */
	boolean verify(@NotNull PasswordAccount account, @NotNull String password);

	/**
	 * Verify a plain-text password using the configured hashing algorithm.
	 *
	 * @param password plain-text password
	 * @param passwordHash stored hash
	 * @return {@code true} when the password matches the stored hash
	 * @throws IllegalStateException when the configured algorithm is missing or unavailable
	 */
	boolean verify(@NotNull String password, @NotNull String passwordHash);

	/**
	 * Verify a plain-text password against a stored hash and hashing method.
	 *
	 * @param password plain-text password
	 * @param passwordHash stored hash
	 * @param hashingMethod hashing method id
	 * @return {@code true} when the password matches the stored hash
	 */
	boolean verify(@NotNull String password, @NotNull String passwordHash, @NotNull String hashingMethod);

	/**
	 * Return the configured hashing algorithm id.
	 *
	 * @return configured algorithm id or {@code null} when not configured
	 */
	@Nullable String primaryAlgorithm();
}
