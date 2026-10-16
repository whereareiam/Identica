package me.whereareiam.identica.provider.credential.cryptography.bcrypt;

import at.favre.lib.crypto.bcrypt.BCrypt;
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

	@DisplayName("Hashes and verifies a password longer than the 72 bytes bcrypt reads")
	@Test
	void verifiesALongPassword() {
		String password = "Secret-123" + "x".repeat(80);
		String hash = bcrypt.hash(password, options(4));

		assertTrue(bcrypt.verify(password, hash));
		assertFalse(bcrypt.verify("Secret-124" + "x".repeat(80), hash));
	}

	@DisplayName("Counts bytes, not characters, so a long multi-byte password works too")
	@Test
	void verifiesALongMultiBytePassword() {
		String password = "Пароль-123" + "密".repeat(30);
		String hash = bcrypt.hash(password, options(4));

		assertTrue(bcrypt.verify(password, hash));
	}

	@DisplayName("Reads only the first 72 bytes, like every common bcrypt implementation")
	@Test
	void readsOnlyTheFirst72Bytes() {
		String prefix = "Secret-123" + "x".repeat(62);
		String hash = bcrypt.hash(prefix + "first", options(4));

		assertTrue(bcrypt.verify(prefix + "second", hash));
	}

	@DisplayName("Verifies hashes of every bcrypt version")
	@Test
	void verifiesEveryVersion() {
		for (BCrypt.Version version : new BCrypt.Version[] {BCrypt.Version.VERSION_2A, BCrypt.Version.VERSION_2B, BCrypt.Version.VERSION_2Y}) {
			String hash = BCrypt.with(version).hashToString(4, "Secret-123".toCharArray());

			assertTrue(bcrypt.verify("Secret-123", hash), hash);
		}
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
