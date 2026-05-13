package me.whereareiam.identica.provider.password.cryptography.argon2;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import me.whereareiam.identica.provider.password.model.CryptographyOptions;
import me.whereareiam.identica.provider.password.CryptographyAlgorithm;
import org.jetbrains.annotations.NotNull;

public class Argon2CryptographyAlgorithm implements CryptographyAlgorithm {
	private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

	@Override
	public @NotNull String id() {
		return "argon2";
	}

	@Override
	public @NotNull String hash(@NotNull String password, @NotNull CryptographyOptions options) {
		int iterations = options.getArgon2Iterations();
		int memoryKb = options.getArgon2MemoryKb();
		int parallelism = options.getArgon2Parallelism();
		if (iterations <= 0)
			throw new IllegalArgumentException("argon2 iterations must be positive");
		if (memoryKb <= 0)
			throw new IllegalArgumentException("argon2 memoryKb must be positive");
		if (parallelism <= 0)
			throw new IllegalArgumentException("argon2 parallelism must be positive");

		return argon2.hash(iterations, memoryKb, parallelism, password.toCharArray());
	}

	@Override
	public boolean verify(@NotNull String password, @NotNull String hash) {
		return argon2.verify(hash, password.toCharArray());
	}
}
