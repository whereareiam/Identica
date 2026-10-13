package me.whereareiam.identica.common.provider.selection.source;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Player Choice Claim Source")
class PlayerChoiceClaimSourceTest {
	private static final String CONNECTION_KEY = "Alice|127.0.0.1||";

	@Mock
	private PipelineStateStore pipelineStateStore;

	@DisplayName("Carries over the player's choice of the pending pipeline, never its proof")
	@Test
	void carriesOverOnlyTheChoice() {
		pending(ProviderSelection.of(
				ProviderClaim.verified("beta", "beta-proof"),
				List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.PLAYER_CHOICE), ProviderClaim.of("beta", ProviderClaimOrigin.ENTRYPOINT)),
				List.of()
		));

		assertEquals(List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.PLAYER_CHOICE)), claims());
	}

	@DisplayName("Claims nothing without a pending pipeline")
	@Test
	void claimsNothingWithoutAPendingPipeline() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		assertTrue(claims().isEmpty());
	}

	private void pending(ProviderSelection selection) {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(RegistrationContext.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.selection(selection)
				.build());
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.of(state));
	}

	private List<ProviderClaim> claims() {
		return List.copyOf(new PlayerChoiceClaimSource(pipelineStateStore).claims(ProviderClaimRequest.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.stage(PrepareStage.HANDSHAKE)
				.connectionKey(CONNECTION_KEY)
				.build()));
	}
}
