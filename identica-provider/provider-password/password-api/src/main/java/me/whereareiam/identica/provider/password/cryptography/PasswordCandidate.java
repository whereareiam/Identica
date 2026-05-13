package me.whereareiam.identica.provider.password.cryptography;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Prepared password hash candidate for storage.
 */
@Getter
@AllArgsConstructor
public class PasswordCandidate {
	private final String passwordHash;
	private final String hashingMethod;
}
