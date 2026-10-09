package me.whereareiam.identica.provider.credential.command;

import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.database.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Management Command")
class ManagementCommandTest {
	private static final String USERNAME = "PlayerOne";
	private static final String SUBJECT = UniqueIdGenerator.offlinePlayerUniqueId(USERNAME).toString();
	private static final UUID ACCOUNT = UUID.randomUUID();

	@Mock
	private CredentialAccountService credentialService;
	@Mock
	private CryptographyService cryptographyService;
	@Mock
	private PasswordRules passwordRules;
	@Mock
	private AccountService accountService;
	@Mock
	private ProviderLinkPersistenceService providerLinks;
	@Mock
	private ProviderProfilePersistenceService providerProfiles;
	@Mock
	private UniqueIdGenerator uniqueIdGenerator;

	private ManagementCommand command;

	@BeforeEach
	void prepare() {
		CredentialMessages messages = new CredentialMessages();
		CredentialMessages.Commands commands = new CredentialMessages.Commands();
		CredentialMessages.Commands.Admin admin = new CredentialMessages.Commands.Admin();
		admin.setRegistered("");
		admin.setNotFound("");
		admin.setAlreadyRegistered("");
		commands.setAdmin(admin);
		messages.setCommands(commands);

		PasswordCandidate candidate = mock(PasswordCandidate.class);
		when(candidate.getPasswordHash()).thenReturn("hash");
		when(candidate.getHashingMethod()).thenReturn("method");
		when(cryptographyService.hash(anyString())).thenReturn(candidate);
		when(credentialService.find(SUBJECT)).thenReturn(Optional.empty());
		when(credentialService.register(eq(SUBJECT), anyString(), anyString(), eq(PasswordChangeReason.ADMIN_SET)))
				.thenReturn(Optional.of(mock(CredentialAccount.class)));
		when(accountService.create(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(providerLinks.findBySubject("credential", SUBJECT)).thenReturn(Optional.empty());

		command = new ManagementCommand(() -> messages, credentialService, cryptographyService, passwordRules,
				accountService, providerLinks, providerProfiles, uniqueIdGenerator);
	}

	@Test
	@DisplayName("registering an unknown username creates the account, its primary link and its profile")
	void createsACompleteAccountForAnUnknownUsername() {
		when(accountService.find(USERNAME)).thenReturn(List.of());
		when(uniqueIdGenerator.resolveConfiguredUniqueId(USERNAME, SUBJECT, null)).thenReturn(ACCOUNT);
		when(providerLinks.findByUniqueId(ACCOUNT)).thenReturn(List.of());

		command.forceRegister(mock(Actor.class), USERNAME, "Secret-123");

		ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
		verify(accountService).create(account.capture());
		assertEquals(ACCOUNT, account.getValue().getUniqueId());
		assertEquals(USERNAME, account.getValue().getUsername());

		AccountProviderLink link = storedLink();
		assertEquals(ACCOUNT, link.getUniqueId());
		assertEquals(SUBJECT, link.getProviderSubject());
		assertTrue(link.isPrimaryLink());

		ArgumentCaptor<AccountProviderProfile> profile = ArgumentCaptor.forClass(AccountProviderProfile.class);
		verify(providerProfiles).upsert(profile.capture());
		assertEquals(USERNAME, profile.getValue().getProviderUsername());
		verify(credentialService).register(SUBJECT, "hash", "method", PasswordChangeReason.ADMIN_SET);
	}

	@Test
	@DisplayName("registering a known username links Credential to its account without replacing the primary link")
	void linksAnExistingAccountWithoutTakingOverItsPrimaryProvider() {
		Account existing = Account.builder().uniqueId(ACCOUNT).username(USERNAME).build();
		when(accountService.find(USERNAME)).thenReturn(List.of(existing));
		when(providerLinks.findByUniqueId(ACCOUNT)).thenReturn(List.of(mock(AccountProviderLink.class)));

		command.forceRegister(mock(Actor.class), USERNAME, "Secret-123");

		verify(accountService, never()).create(any(Account.class));
		assertFalse(storedLink().isPrimaryLink());
	}

	private AccountProviderLink storedLink() {
		ArgumentCaptor<AccountProviderLink> link = ArgumentCaptor.forClass(AccountProviderLink.class);
		verify(providerLinks).upsert(link.capture());
		assertEquals("credential", link.getValue().getProviderId());
		return link.getValue();
	}
}
