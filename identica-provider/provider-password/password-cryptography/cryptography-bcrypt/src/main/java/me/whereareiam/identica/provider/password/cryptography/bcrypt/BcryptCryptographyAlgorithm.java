package me.whereareiam.identica.provider.password.cryptography.bcrypt;

import at.favre.lib.crypto.bcrypt.BCrypt;
import me.whereareiam.identica.provider.password.model.CryptographyOptions;
import me.whereareiam.identica.provider.password.CryptographyAlgorithm;
import org.jetbrains.annotations.NotNull;

public class BcryptCryptographyAlgorithm implements CryptographyAlgorithm {
	@Override
	public @NotNull String id() {
		return "bcrypt";
	}

	@Override
	public @NotNull String hash(@NotNull String password, @NotNull CryptographyOptions options) {
		int cost = options.getBcryptCost();
		if (cost <= 0)
			throw new IllegalArgumentException("bcrypt cost must be positive");
		return BCrypt.withDefaults().hashToString(cost, password.toCharArray());
	}

	@Override
	public boolean verify(@NotNull String password, @NotNull String hash) {
		return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
	}
}
