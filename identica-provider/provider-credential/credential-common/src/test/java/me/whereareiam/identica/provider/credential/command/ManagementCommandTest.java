package me.whereareiam.identica.provider.credential.command;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.resolver.CredentialSubjectResolver;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	private static final String RESERVATION = "username:playerone";
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
	private AccountReservationPersistenceService reservations;
	@Mock
	private UniqueIdGenerator uniqueIdGenerator;
	@Mock
	private Actor sender;

	private ManagementCommand command;

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class))).thenAnswer(call -> {
			SerializerContent content = call.getArgument(0, SerializerContent.class);
			String rendered = content.getMessage();
			for (Map.Entry<String, String> placeholder : content.getPlaceholders().entrySet())
				rendered = rendered.replace("{" + placeholder.getKey() + "}", placeholder.getValue());

			return Component.text(rendered);
		});
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void prepare() {
		CredentialMessages messages = new CredentialMessages();
		CredentialMessages.Commands commands = new CredentialMessages.Commands();
		CredentialMessages.Commands.Admin admin = new CredentialMessages.Commands.Admin();
		admin.setRegistered("registered");
		admin.setPasswordSet("password-set");
		admin.setNotFound("not-found");
		admin.setAlreadyRegistered("already-registered");
		admin.setAccountExists("exists {target} {username} {uniqueId}");
		admin.setReservationTaken("reservation-taken {target} {uniqueId} {username}");
		commands.setAdmin(admin);
		messages.setCommands(commands);

		PasswordCandidate candidate = mock(PasswordCandidate.class);
		when(candidate.getPasswordHash()).thenReturn("hash");
		when(candidate.getHashingMethod()).thenReturn("method");
		when(cryptographyService.hash(anyString())).thenReturn(candidate);
		when(credentialService.find(anyString())).thenReturn(Optional.empty());
		when(credentialService.register(eq(SUBJECT), anyString(), anyString(), eq(PasswordChangeReason.ADMIN_SET)))
				.thenReturn(Optional.of(mock(CredentialAccount.class)));
		when(accountService.find(anyString())).thenReturn(List.of());
		when(accountService.find(any(UUID.class))).thenReturn(Optional.empty());
		when(accountService.create(any(Account.class), any(), any())).thenReturn(UsernameWriteResult.written());
		when(providerLinks.findBySubject(anyString(), anyString())).thenReturn(Optional.empty());
		when(providerLinks.findByUniqueIdAndProviderId(any(UUID.class), anyString())).thenReturn(Optional.empty());
		when(reservations.find(anyString())).thenReturn(Optional.empty());
		when(uniqueIdGenerator.resolveConfiguredUniqueId(USERNAME, SUBJECT, null)).thenReturn(ACCOUNT);

		command = new ManagementCommand(() -> messages, credentialService, cryptographyService, passwordRules,
				accountService, providerLinks, providerProfiles, reservations, uniqueIdGenerator,
				new CredentialSubjectResolver(accountService, providerLinks));
	}

	@Test
	@DisplayName("registering an unknown username creates the account, its primary link and its profile")
	void createsACompleteAccountForAnUnknownUsername() {
		command.forceRegister(sender, USERNAME, "Secret-123");

		ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
		verify(accountService).create(account.capture(), eq(AccountUsernameSource.PROVIDER), eq("credential"));
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
		assertEquals("registered", answer());
	}

	@Test
	@DisplayName("registering a username an account of another provider holds in another case names that account and registers nothing")
	void refusesAUsernameAnotherAccountHolds() {
		UUID holder = UUID.randomUUID();
		when(accountService.find(USERNAME)).thenReturn(List.of(Account.builder().uniqueId(holder).username("playerone").build()));

		command.forceRegister(sender, USERNAME, "Secret-123");

		assertEquals("exists " + USERNAME + " playerone " + holder, answer());
		assertRegisteredNothing();
	}

	@Test
	@DisplayName("registering a username whose Credential subject belongs to a renamed account names that account and registers nothing")
	void refusesASubjectAnAccountHolds() {
		when(providerLinks.findBySubject("credential", SUBJECT)).thenReturn(Optional.of(AccountProviderLink.builder()
				.uniqueId(ACCOUNT)
				.providerId("credential")
				.providerSubject(SUBJECT)
				.build()));
		when(accountService.find(ACCOUNT)).thenReturn(Optional.of(Account.builder().uniqueId(ACCOUNT).username("Renamed").build()));

		command.forceRegister(sender, USERNAME, "Secret-123");

		assertEquals("exists " + USERNAME + " Renamed " + ACCOUNT, answer());
		assertRegisteredNothing();
	}

	@Test
	@DisplayName("a username taken between the check and the creation names its holder and registers nothing")
	void refusesAUsernameTakenWhileRegistering() {
		UUID holder = UUID.randomUUID();
		when(accountService.create(any(Account.class), any(), any())).thenReturn(UsernameWriteResult.taken(holder));
		when(accountService.find(holder)).thenReturn(Optional.of(Account.builder().uniqueId(holder).username("PLAYERONE").build()));

		command.forceRegister(sender, USERNAME, "Secret-123");

		assertEquals("exists " + USERNAME + " PLAYERONE " + holder, answer());
		verify(providerLinks, never()).upsert(any());
		verify(credentialService, never()).register(anyString(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("registering a username with a reserved UUID creates the account with that UUID and uses the reservation up")
	void givesANewAccountTheReservedUuid() {
		UUID reserved = UUID.randomUUID();
		when(reservations.find(RESERVATION)).thenReturn(Optional.of(reserved));

		command.forceRegister(sender, USERNAME, "Secret-123");

		ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
		verify(accountService).create(account.capture(), any(), any());
		assertEquals(reserved, account.getValue().getUniqueId());
		assertEquals(reserved, storedLink().getUniqueId());
		verify(reservations).delete(RESERVATION);
		verify(uniqueIdGenerator, never()).resolveConfiguredUniqueId(anyString(), anyString(), any());
	}

	@Test
	@DisplayName("PREMIUM assigns its own UUID even when a UUID is reserved")
	void ignoresTheReservationInPremiumMode() {
		when(uniqueIdGenerator.requiresConfiguredUniqueId()).thenReturn(true);
		when(reservations.find(RESERVATION)).thenReturn(Optional.of(UUID.randomUUID()));

		command.forceRegister(sender, USERNAME, "Secret-123");

		ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
		verify(accountService).create(account.capture(), any(), any());
		assertEquals(ACCOUNT, account.getValue().getUniqueId());
	}

	@Test
	@DisplayName("registering a username whose reserved UUID belongs to an account names that account and registers nothing")
	void refusesAReservedUuidThatIsTaken() {
		UUID reserved = UUID.randomUUID();
		when(reservations.find(RESERVATION)).thenReturn(Optional.of(reserved));
		when(accountService.find(reserved)).thenReturn(Optional.of(Account.builder().uniqueId(reserved).username("Other").build()));

		command.forceRegister(sender, USERNAME, "Secret-123");

		assertEquals("reservation-taken " + USERNAME + " " + reserved + " Other", answer());
		assertRegisteredNothing();
		verify(reservations, never()).delete(anyString());
	}

	@Test
	@DisplayName("setting a password in another case than the Credential account's username updates that account")
	void setsThePasswordOfTheCredentialAccountOfAnotherCase() {
		String registered = UniqueIdGenerator.offlinePlayerUniqueId("playerone").toString();
		when(accountService.find(USERNAME)).thenReturn(List.of(Account.builder().uniqueId(ACCOUNT).username("playerone").build()));
		when(providerLinks.findByUniqueIdAndProviderId(ACCOUNT, "credential")).thenReturn(Optional.of(AccountProviderLink.builder()
				.uniqueId(ACCOUNT)
				.providerId("credential")
				.providerSubject(registered)
				.build()));
		CredentialAccount credential = mock(CredentialAccount.class);
		when(credentialService.find(registered)).thenReturn(Optional.of(credential));

		command.setPassword(sender, USERNAME, "Secret-123");

		verify(credentialService).updatePassword(credential, "hash", "method", PasswordChangeReason.ADMIN_SET);
		assertEquals("password-set", answer());
	}

	private void assertRegisteredNothing() {
		verify(accountService, never()).create(any(Account.class), any(), any());
		verify(providerLinks, never()).upsert(any());
		verify(credentialService, never()).register(anyString(), anyString(), anyString(), any());
	}

	private String answer() {
		ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
		verify(sender).sendMessage(message.capture());
		return PlainTextComponentSerializer.plainText().serialize(message.getValue());
	}

	private AccountProviderLink storedLink() {
		ArgumentCaptor<AccountProviderLink> link = ArgumentCaptor.forClass(AccountProviderLink.class);
		verify(providerLinks).upsert(link.capture());
		assertEquals("credential", link.getValue().getProviderId());
		return link.getValue();
	}
}
