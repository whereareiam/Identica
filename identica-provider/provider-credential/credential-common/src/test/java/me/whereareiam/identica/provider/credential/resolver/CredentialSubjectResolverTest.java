package me.whereareiam.identica.provider.credential.resolver;

import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Credential Subject Resolver")
class CredentialSubjectResolverTest {
	private final AccountService accounts = mock(AccountService.class);
	private final ProviderLinkPersistenceService links = mock(ProviderLinkPersistenceService.class);
	private final CredentialSubjectResolver resolver = new CredentialSubjectResolver(accounts, links);

	@BeforeEach
	void prepare() {
		when(links.findBySubject(anyString(), anyString())).thenReturn(Optional.empty());
		when(links.findByUniqueIdAndProviderId(any(UUID.class), anyString())).thenReturn(Optional.empty());
		when(accounts.find(anyString())).thenReturn(List.of());
	}

	@DisplayName("Derives the credential provider subject from the username")
	@Test
	void resolvesCredentialSubjectFromUsername() {
		SubjectResolution resolution = resolve("PlayerOne");

		assertNotNull(resolution);
		assertEquals(CredentialConstants.PROVIDER_ID, resolution.getProviderId());
		assertEquals(offline("PlayerOne"), resolution.getProviderSubject());
	}

	@DisplayName("Resolves the subject of the Credential account registered under the username in another case")
	@Test
	void resolvesTheSubjectOfTheAccountRegisteredInAnotherCase() {
		UUID account = UUID.randomUUID();
		when(accounts.find("mrbeast")).thenReturn(List.of(account(account, "MrBeast")));
		when(links.findByUniqueIdAndProviderId(account, CredentialConstants.PROVIDER_ID))
				.thenReturn(Optional.of(link(account, offline("MrBeast"))));

		SubjectResolution resolution = resolve("mrbeast");

		assertNotNull(resolution);
		assertEquals(offline("MrBeast"), resolution.getProviderSubject());
	}

	@DisplayName("Keeps the username's own subject when a Credential account uses it")
	@Test
	void keepsTheExactSubjectWhenItIsRegistered() {
		UUID account = UUID.randomUUID();
		when(links.findBySubject(CredentialConstants.PROVIDER_ID, offline("mrbeast")))
				.thenReturn(Optional.of(link(account, offline("mrbeast"))));
		when(accounts.find("mrbeast")).thenReturn(List.of(account(UUID.randomUUID(), "MrBeast")));

		SubjectResolution resolution = resolve("mrbeast");

		assertNotNull(resolution);
		assertEquals(offline("mrbeast"), resolution.getProviderSubject());
	}

	@DisplayName("Keeps the username's own subject when accounts of several cases have Credential")
	@Test
	void keepsTheExactSubjectWhenSeveralAccountsMatch() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		when(accounts.find("mrbeast")).thenReturn(List.of(account(first, "MrBeast"), account(second, "MRBEAST")));
		when(links.findByUniqueIdAndProviderId(first, CredentialConstants.PROVIDER_ID))
				.thenReturn(Optional.of(link(first, offline("MrBeast"))));
		when(links.findByUniqueIdAndProviderId(second, CredentialConstants.PROVIDER_ID))
				.thenReturn(Optional.of(link(second, offline("MRBEAST"))));

		SubjectResolution resolution = resolve("mrbeast");

		assertNotNull(resolution);
		assertEquals(offline("mrbeast"), resolution.getProviderSubject());
	}

	@DisplayName("Ignores an account holding the username in another case without Credential")
	@Test
	void ignoresAnAccountWithoutCredential() {
		when(accounts.find("mrbeast")).thenReturn(List.of(account(UUID.randomUUID(), "MrBeast")));

		SubjectResolution resolution = resolve("mrbeast");

		assertNotNull(resolution);
		assertEquals(offline("mrbeast"), resolution.getProviderSubject());
	}

	@DisplayName("Rejects blank usernames")
	@Test
	void doesNotSupportBlankUsernames() {
		assertFalse(resolver.supports(SubjectResolveContext.builder()
				.identity(new ConnectionIdentity("", "127.0.0.1"))
				.build()));
		assertNull(resolve(""));
	}

	private SubjectResolution resolve(String username) {
		return resolver.resolve(SubjectResolveContext.builder()
				.identity(new ConnectionIdentity(username, "127.0.0.1"))
				.build());
	}

	private static Account account(UUID uniqueId, String username) {
		return Account.builder()
				.uniqueId(uniqueId)
				.username(username)
				.build();
	}

	private static AccountProviderLink link(UUID account, String subject) {
		return AccountProviderLink.builder()
				.uniqueId(account)
				.providerId(CredentialConstants.PROVIDER_ID)
				.providerSubject(subject)
				.primaryLink(true)
				.build();
	}

	private static String offline(String username) {
		return UniqueIdGenerator.offlinePlayerUniqueId(username).toString();
	}
}
