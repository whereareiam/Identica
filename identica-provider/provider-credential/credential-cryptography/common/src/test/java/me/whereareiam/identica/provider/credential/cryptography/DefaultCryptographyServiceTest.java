package me.whereareiam.identica.provider.credential.cryptography;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.provider.credential.CryptographyAlgorithm;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.event.account.password.PasswordVerifiedEvent;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.model.CryptographyOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Default Cryptography Service")
class DefaultCryptographyServiceTest {
	private final CredentialSettings settings = settings("first");
	private final EventManager events = mock(EventManager.class);
	private final DefaultCryptographyService service = new DefaultCryptographyService(
			new DefaultCryptographyRegistry(Set.of(new Prefixing("first"), new Prefixing("second"))),
			() -> settings,
			events
	);

	@DisplayName("Hashes new passwords with the configured algorithm and names it")
	@Test
	void hashesWithTheConfiguredAlgorithm() {
		PasswordCandidate candidate = service.hash("Secret-123");

		assertEquals("first:Secret-123", candidate.getPasswordHash());
		assertEquals("first", candidate.getHashingMethod());
	}

	@DisplayName("Resolves the configured algorithm regardless of case")
	@Test
	void resolvesTheAlgorithmRegardlessOfCase() {
		settings.getCryptography().setAlgorithm("SECOND");

		assertEquals("second", service.hash("Secret-123").getHashingMethod());
	}

	@DisplayName("Verifies a stored password with the algorithm it was hashed with, not the configured one")
	@Test
	void verifiesWithTheStoredAlgorithm() {
		settings.getCryptography().setAlgorithm("second");

		assertTrue(service.verify(account("first:Secret-123", "first"), "Secret-123"));
		assertFalse(service.verify(account("first:Secret-123", "first"), "Secret-124"));
	}

	@DisplayName("Announces a verified password, so a login can upgrade it, but never a refused one")
	@Test
	void announcesOnlyVerifiedPasswords() {
		CredentialAccount account = account("first:Secret-123", "first");

		service.verify(account, "Secret-124");
		verify(events, never()).call(any());

		service.verify(account, "Secret-123");
		verify(events).call(argThat(event -> event instanceof PasswordVerifiedEvent verified
				&& verified.getCredential() == account && "Secret-123".equals(verified.getPassword())));
	}

	@DisplayName("Refuses a password stored with an algorithm that is not installed")
	@Test
	void refusesAnUnknownStoredAlgorithm() {
		assertFalse(service.verify(account("gone:Secret-123", "gone"), "Secret-123"));
	}

	@DisplayName("Creates no hash while the configured algorithm is missing or blank")
	@Test
	void hashesNothingWithoutAConfiguredAlgorithm() {
		settings.getCryptography().setAlgorithm("gone");
		assertNull(service.hash("Secret-123"));

		settings.getCryptography().setAlgorithm(" ");
		assertNull(service.hash("Secret-123"));
	}

	private static CredentialAccount account(String hash, String method) {
		return CredentialAccount.builder()
				.providerId("credential")
				.providerSubject("alice")
				.passwordHash(hash)
				.hashingMethod(method)
				.build();
	}

	private static CredentialSettings settings(String algorithm) {
		CredentialSettings.Cryptography cryptography = new CredentialSettings.Cryptography();
		cryptography.setAlgorithm(algorithm);
		cryptography.setAutoupgrade(true);
		CredentialSettings settings = new CredentialSettings();
		settings.setCryptography(cryptography);
		return settings;
	}

	/**
	 * Stands in for a real hasher: its hash is the password behind the algorithm's id.
	 */
	private static final class Prefixing implements CryptographyAlgorithm {
		private final String id;

		private Prefixing(String id) {
			this.id = id;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull String hash(@NotNull String password, @NotNull CryptographyOptions options) {
			return id + ":" + password;
		}

		@Override
		public boolean verify(@NotNull String password, @NotNull String hash) {
			return hash.equals(id + ":" + password);
		}
	}
}
