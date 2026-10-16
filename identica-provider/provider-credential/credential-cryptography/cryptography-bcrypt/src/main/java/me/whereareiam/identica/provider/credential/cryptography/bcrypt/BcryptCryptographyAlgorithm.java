package me.whereareiam.identica.provider.credential.cryptography.bcrypt;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategy;
import me.whereareiam.identica.provider.credential.CryptographyAlgorithm;
import me.whereareiam.identica.provider.credential.model.CryptographyOptions;
import org.jetbrains.annotations.NotNull;

public class BcryptCryptographyAlgorithm implements CryptographyAlgorithm {
	/**
	 * Bcrypt reads at most 72 bytes of a password. Longer passwords are cut there, as every common bcrypt
	 * implementation does, instead of being refused.
	 */
	private static final LongPasswordStrategy TRUNCATE = LongPasswordStrategies.truncate(BCrypt.Version.VERSION_2A);

	@Override
	public @NotNull String id() {
		return "bcrypt";
	}

	@Override
	public @NotNull String hash(@NotNull String password, @NotNull CryptographyOptions options) {
		int cost = options.getBcryptCost();
		if (cost <= 0)
			throw new IllegalArgumentException("bcrypt cost must be positive");
		return BCrypt.with(TRUNCATE).hashToString(cost, password.toCharArray());
	}

	@Override
	public boolean verify(@NotNull String password, @NotNull String hash) {
		// Without a fixed version the verifier reads it from the hash, so $2a$, $2b$ and $2y$ hashes all verify.
		return BCrypt.verifyer(null, TRUNCATE).verify(password.toCharArray(), hash).verified;
	}
}
