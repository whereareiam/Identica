package me.whereareiam.identica.common.provider.selection.source;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Link Claim Source")
class AccountLinkClaimSourceTest {
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;

	@DisplayName("Claims the connection for every provider that verified a subject linked to an account")
	@Test
	void claimsForLinkedSubjects() {
		when(providerLinkPersistenceService.findBySubject("alpha", "alpha-subject")).thenReturn(Optional.of(AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId("alpha")
				.providerSubject("alpha-subject")
				.build()));
		when(providerLinkPersistenceService.findBySubject("beta", "beta-subject")).thenReturn(Optional.empty());

		ProviderClaimRequest request = ProviderClaimRequest.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.stage(PrepareStage.PROFILE)
				.subjects(List.of(
						SubjectResolution.builder().providerId("alpha").providerSubject("alpha-subject").verified(true).build(),
						SubjectResolution.builder().providerId("beta").providerSubject("beta-subject").verified(true).build()
				))
				.build();

		assertEquals(
				List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.ACCOUNT_LINK)),
				new AccountLinkClaimSource(providerLinkPersistenceService).claims(request)
		);
	}

	@DisplayName("Makes no account link claim for a subject derived from the username")
	@Test
	void ignoresADerivedSubject() {
		ProviderClaimRequest request = ProviderClaimRequest.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.stage(PrepareStage.HANDSHAKE)
				.subjects(List.of(SubjectResolution.builder().providerId("alpha").providerSubject("alpha-subject").build()))
				.build();

		assertTrue(new AccountLinkClaimSource(providerLinkPersistenceService).claims(request).isEmpty());
		verifyNoInteractions(providerLinkPersistenceService);
	}
}
