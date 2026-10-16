package me.whereareiam.identica.provider.credential.account;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.event.account.password.PasswordVerifiedEvent;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Autoupgrade Lifecycle")
class AutoupgradeLifecycleTest {
	private final CredentialSettings settings = new CredentialSettings();
	private final CryptographyService cryptography = mock(CryptographyService.class);
	private final CredentialAccountService credentials = mock(CredentialAccountService.class);
	private final AutoupgradeLifecycle lifecycle = new AutoupgradeLifecycle(
			() -> settings,
			cryptography,
			credentials,
			mock(EventManager.class)
	);

	@DisplayName("Rehashes a password stored with another algorithm after a successful login")
	@Test
	void rehashesAnOlderAlgorithm() {
		configure("argon2", true);
		CredentialAccount account = account("bcrypt");
		when(cryptography.hash("Secret-123")).thenReturn(new PasswordCandidate("argon2-hash", "argon2"));

		lifecycle.onPasswordVerified(new PasswordVerifiedEvent(account, "Secret-123"));

		verify(credentials).updatePassword(account, "argon2-hash", "argon2", PasswordChangeReason.REHASH);
	}

	@DisplayName("Leaves a password stored with the configured algorithm, whatever its case")
	@Test
	void leavesTheConfiguredAlgorithm() {
		configure("BCrypt", true);

		lifecycle.onPasswordVerified(new PasswordVerifiedEvent(account("bcrypt"), "Secret-123"));

		verifyNoInteractions(cryptography, credentials);
	}

	@DisplayName("Leaves every password while autoupgrade is off")
	@Test
	void leavesPasswordsWhileTurnedOff() {
		configure("argon2", false);

		lifecycle.onPasswordVerified(new PasswordVerifiedEvent(account("bcrypt"), "Secret-123"));

		verifyNoInteractions(cryptography, credentials);
	}

	@DisplayName("Keeps the stored password when the configured algorithm cannot hash")
	@Test
	void keepsThePasswordWhenHashingFails() {
		configure("argon2", true);
		when(cryptography.hash(anyString())).thenReturn(null);

		lifecycle.onPasswordVerified(new PasswordVerifiedEvent(account("bcrypt"), "Secret-123"));

		verify(credentials, never()).updatePassword(any(), any(), any(), any());
	}

	private void configure(String algorithm, boolean autoupgrade) {
		CredentialSettings.Cryptography cryptography = new CredentialSettings.Cryptography();
		cryptography.setAlgorithm(algorithm);
		cryptography.setAutoupgrade(autoupgrade);
		settings.setCryptography(cryptography);
	}

	private static CredentialAccount account(String hashingMethod) {
		return CredentialAccount.builder()
				.providerId("credential")
				.providerSubject("alice")
				.passwordHash("stored-hash")
				.hashingMethod(hashingMethod)
				.build();
	}
}
