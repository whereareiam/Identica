package me.whereareiam.identica.provider.credential.cryptography.argon2;

import me.whereareiam.identica.provider.credential.model.CryptographyOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Argon2 Cryptography Algorithm")
class Argon2CryptographyAlgorithmTest {
	private final Argon2CryptographyAlgorithm argon2 = new Argon2CryptographyAlgorithm();

	@DisplayName("Verifies the password it hashed and refuses another")
	@Test
	void verifiesItsOwnHash() {
		String hash = argon2.hash("Secret-123", options(1, 1024, 1));

		assertTrue(argon2.verify("Secret-123", hash));
		assertFalse(argon2.verify("Secret-124", hash));
	}

	@DisplayName("Hashes Argon2id with the configured iterations, memory and parallelism")
	@Test
	void usesTheConfiguredParameters() {
		String hash = argon2.hash("Secret-123", options(2, 2048, 1));

		assertTrue(hash.startsWith("$argon2id$"), hash);
		assertTrue(hash.contains("m=2048,t=2,p=1"), hash);
	}

	@DisplayName("Verifies a password longer than bcrypt reads")
	@Test
	void verifiesALongPassword() {
		String password = "Secret-123" + "x".repeat(80);
		String hash = argon2.hash(password, options(1, 1024, 1));

		assertTrue(argon2.verify(password, hash));
		assertFalse(argon2.verify(password + "y", hash));
	}

	@DisplayName("Refuses parameters that are not positive")
	@Test
	void refusesNonPositiveParameters() {
		assertThrows(IllegalArgumentException.class, () -> argon2.hash("Secret-123", options(0, 1024, 1)));
		assertThrows(IllegalArgumentException.class, () -> argon2.hash("Secret-123", options(1, 0, 1)));
		assertThrows(IllegalArgumentException.class, () -> argon2.hash("Secret-123", options(1, 1024, 0)));
	}

	private static CryptographyOptions options(int iterations, int memoryKb, int parallelism) {
		CryptographyOptions options = new CryptographyOptions();
		options.setArgon2Iterations(iterations);
		options.setArgon2MemoryKb(memoryKb);
		options.setArgon2Parallelism(parallelism);
		return options;
	}
}
