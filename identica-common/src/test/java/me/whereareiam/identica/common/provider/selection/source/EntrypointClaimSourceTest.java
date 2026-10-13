package me.whereareiam.identica.common.provider.selection.source;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Entrypoint Claim Source")
class EntrypointClaimSourceTest {
	@Mock
	private ProviderOperations providerOperations;

	@DisplayName("Claims the connection for the provider of the hostname it arrived through")
	@Test
	void claimsForTheProviderOfTheHostname() {
		when(providerOperations.resolveEntrypoint("alpha.example.org", 25565)).thenReturn(ResolvedEntrypoint.builder()
				.providerId("alpha")
				.host("alpha.example.org")
				.build());

		assertEquals(
				List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.ENTRYPOINT)),
				new EntrypointClaimSource(providerOperations).claims(request(new ConnectionIdentity.Origin("alpha.example.org", 25565)))
		);
	}

	@DisplayName("Claims nothing for a hostname no provider answers on")
	@Test
	void claimsNothingForAnUnknownHostname() {
		assertTrue(new EntrypointClaimSource(providerOperations)
				.claims(request(new ConnectionIdentity.Origin("play.example.org", 25565)))
				.isEmpty());
	}

	@DisplayName("Claims nothing for a connection without a hostname")
	@Test
	void claimsNothingWithoutAHostname() {
		assertTrue(new EntrypointClaimSource(providerOperations).claims(request(null)).isEmpty());
		verifyNoInteractions(providerOperations);
	}

	private static ProviderClaimRequest request(ConnectionIdentity.Origin origin) {
		ConnectionIdentity identity = new ConnectionIdentity("Alice", "127.0.0.1");
		identity.setOrigin(origin);
		return ProviderClaimRequest.builder()
				.identity(identity)
				.stage(PrepareStage.HANDSHAKE)
				.build();
	}
}
