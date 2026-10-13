package me.whereareiam.identica.model.provider.selection;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Provider Selection")
class ProviderSelectionTest {
	private final ProviderSelection selection = ProviderSelection.of(
			ProviderClaim.verified("alpha", "alpha-proof"),
			List.of(ProviderClaim.of("beta", ProviderClaimOrigin.PLAYER_CHOICE)),
			List.of("gamma")
	);

	@DisplayName("Takes its origin from the winning claim")
	@Test
	void takesItsOriginFromTheWinner() {
		assertEquals("alpha", selection.getWinnerProviderId());
		assertEquals(ProviderClaimOrigin.VERIFIED_SUBJECT, selection.getOrigin());
		assertTrue(selection.isWonBy("ALPHA"));
		assertFalse(selection.isWonBy("beta"));
	}

	@DisplayName("Finds claims, proof and exclusions")
	@Test
	void findsClaimsProofAndExclusions() {
		assertTrue(selection.hasClaim("beta", ProviderClaimOrigin.PLAYER_CHOICE));
		assertEquals("alpha-proof", selection.findVerifiedSubject("alpha").orElseThrow());
		assertTrue(selection.findVerifiedSubject("beta").isEmpty());
		assertTrue(selection.isExcluded("Gamma"));
	}

	@DisplayName("A verified subject claim needs its subject")
	@Test
	void verifiedSubjectClaimNeedsItsSubject() {
		assertThrows(IllegalArgumentException.class, () -> ProviderClaim.of("alpha", ProviderClaimOrigin.VERIFIED_SUBJECT));
		assertThrows(IllegalArgumentException.class, () -> ProviderClaim.verified("alpha", " "));
	}

	@DisplayName("Is saved and read back with the pending pipeline of its scenario")
	@Test
	void isSavedWithThePendingPipeline() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(RegistrationContext.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.selection(selection)
				.build());

		PipelineState restored = PipelineState.restore(state.store());

		assertEquals(selection, restored.getScenario(PipelineType.REGISTRATION).getSelection());
	}
}
