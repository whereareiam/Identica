package me.whereareiam.identica.common.provider.selection.source;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Discovery Claim Source")
class DiscoveryClaimSourceTest {
	@DisplayName("Claims a verified subject with its proof and a derived one as discovery")
	@Test
	void claimsVerifiedAndDerivedSubjects() {
		ProviderClaimRequest request = ProviderClaimRequest.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.stage(PrepareStage.PROFILE)
				.subjects(List.of(
						SubjectResolution.builder().providerId("alpha").providerSubject("alpha-subject").verified(true).build(),
						SubjectResolution.builder().providerId("beta").providerSubject("beta-subject").build()
				))
				.build();

		assertEquals(List.of(
				ProviderClaim.verified("alpha", "alpha-subject"),
				ProviderClaim.of("beta", ProviderClaimOrigin.DISCOVERY)
		), new DiscoveryClaimSource().claims(request));
	}
}
