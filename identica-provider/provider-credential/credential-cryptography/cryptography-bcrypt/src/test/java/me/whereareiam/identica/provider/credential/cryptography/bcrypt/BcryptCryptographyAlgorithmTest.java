package me.whereareiam.identica.provider.credential.cryptography.bcrypt;

import me.whereareiam.identica.provider.credential.model.CryptographyOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bcrypt Cryptography Algorithm")
class BcryptCryptographyAlgorithmTest {
	private final BcryptCryptographyAlgorithm bcrypt = new BcryptCryptographyAlgorithm();

	@DisplayName("Verifies the password it hashed and refuses another")
	@Test
	void verifiesItsOwnHash() {
		String hash = bcrypt.hash("Secret-123", options(4));

		assertTrue(bcrypt.verify("Secret-123", hash));
		assertFalse(bcrypt.verify("Secret-124", hash));
	}

	@DisplayName("Hashes with the configured cost and a fresh salt each time")
	@Test
	void usesTheConfiguredCostAndAFreshSalt() {
		String first = bcrypt.hash("Secret-123", options(5));
		String second = bcrypt.hash("Secret-123", options(5));

		assertTrue(first.startsWith("$2a$05$"), first);
		assertNotEquals(first, second);
	}

	@DisplayName("Refuses a cost that is not positive")
	@Test
	void refusesANonPositiveCost() {
		assertThrows(IllegalArgumentException.class, () -> bcrypt.hash("Secret-123", options(0)));
	}

	private static CryptographyOptions options(int cost) {
		CryptographyOptions options = new CryptographyOptions();
		options.setBcryptCost(cost);
		return options;
	}
}
