package me.whereareiam.identica.common.provider.selection.source;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Name Match Claim Source")
class NameMatchClaimSourceTest {
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private ProviderOperations providerOperations;

	@DisplayName("Claims the connection for the provider an account with its username prefers")
	@Test
	void claimsForThePreferredProviderOfTheAccount() {
		UUID uniqueId = UUID.randomUUID();
		List<AccountProviderLink> links = List.of(AccountProviderLink.builder()
				.uniqueId(uniqueId)
				.providerId("alpha")
				.providerSubject("alpha-subject")
				.primaryLink(true)
				.build());
		when(accountPersistenceService.findByUsername("Alice"))
				.thenReturn(List.of(Account.builder().uniqueId(uniqueId).username("Alice").build()));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(links);
		when(providerOperations.selectPreferredLink(links)).thenReturn(links.getFirst());

		assertEquals(List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.NAME_MATCH)), source().claims(request("Alice")));
	}

	@DisplayName("Claims nothing for a username without an account")
	@Test
	void claimsNothingWithoutAnAccount() {
		when(accountPersistenceService.findByUsername("Alice")).thenReturn(List.of());

		assertTrue(source().claims(request("Alice")).isEmpty());
	}

	private NameMatchClaimSource source() {
		return new NameMatchClaimSource(accountPersistenceService, providerLinkPersistenceService, providerOperations);
	}

	private static ProviderClaimRequest request(String username) {
		return ProviderClaimRequest.builder()
				.identity(new ConnectionIdentity(username, "127.0.0.1"))
				.stage(PrepareStage.HANDSHAKE)
				.build();
	}
}
