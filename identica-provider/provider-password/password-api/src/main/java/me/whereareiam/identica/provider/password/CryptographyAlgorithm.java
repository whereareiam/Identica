package me.whereareiam.identica.provider.password;

import me.whereareiam.identica.provider.password.model.CryptographyOptions;
import org.jetbrains.annotations.NotNull;

public interface CryptographyAlgorithm {
	@NotNull String id();

	@NotNull String hash(@NotNull String password, @NotNull CryptographyOptions options);

	boolean verify(@NotNull String password, @NotNull String hash);
}
