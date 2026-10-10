package me.whereareiam.identica.engine.pipeline.prepare.runtime;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Known Account Resolver")
class KnownAccountResolverTest {
	private static final ConnectionIdentity IDENTITY = new ConnectionIdentity("PlayerOne", "127.0.0.1");

	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private AccountPersistenceService accountPersistenceService;

	private KnownAccountResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new KnownAccountResolver(providerOperations, providerLinkPersistenceService, accountPersistenceService);
	}

	@DisplayName("Finds the account through the subject a provider resolves for the connection")
	@Test
	void resolvesTheAccountOfTheSubject() {
		UUID uniqueId = UUID.randomUUID();
		AccountProviderLink link = link(uniqueId, "premium");
		when(providerOperations.discoverSubject(any())).thenReturn(subject("premium", "premium-subject"));
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject")).thenReturn(Optional.of(link));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(List.of(link));
		when(providerOperations.selectPreferredLink(List.of(link))).thenReturn(link);

		assertSame(link, resolver.resolvePreferredLink(IDENTITY));
		verify(accountPersistenceService, never()).findByUsername(any());
	}

	@DisplayName("Falls back to the only account that uses the username")
	@Test
	void resolvesTheAccountOfTheUsername() {
		UUID uniqueId = UUID.randomUUID();
		AccountProviderLink link = link(uniqueId, "credential");
		when(providerOperations.discoverSubject(any())).thenReturn(subject("credential", "credential-subject"));
		when(providerLinkPersistenceService.findBySubject("credential", "credential-subject")).thenReturn(Optional.empty());
		when(accountPersistenceService.findByUsername("PlayerOne")).thenReturn(List.of(account(uniqueId)));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(List.of(link));
		when(providerOperations.selectPreferredLink(List.of(link))).thenReturn(link);

		assertSame(link, resolver.resolvePreferredLink(IDENTITY));
	}

	@DisplayName("Knows no account when several accounts use the username")
	@Test
	void resolvesNothingForAnAmbiguousUsername() {
		when(providerOperations.discoverSubject(any())).thenReturn(null);
		when(accountPersistenceService.findByUsername("PlayerOne"))
				.thenReturn(List.of(account(UUID.randomUUID()), account(UUID.randomUUID())));

		assertNull(resolver.resolvePreferredLink(IDENTITY));
	}

	private SubjectResolution subject(String providerId, String providerSubject) {
		return SubjectResolution.builder()
				.providerId(providerId)
				.providerSubject(providerSubject)
				.build();
	}

	private Account account(UUID uniqueId) {
		return Account.builder()
				.uniqueId(uniqueId)
				.username("PlayerOne")
				.build();
	}

	private AccountProviderLink link(UUID uniqueId, String providerId) {
		return AccountProviderLink.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.providerSubject(providerId + "-subject")
				.primaryLink(true)
				.build();
	}
}
