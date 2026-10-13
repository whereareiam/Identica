package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Restore Prepare State Phase")
class RestorePrepareStatePhaseTest {
	private static final String CONNECTION_KEY = "Alice|127.0.0.1||";

	@Mock
	private PrepareStateStore prepareStateStore;
	@Mock
	private ProviderManager providerManager;

	@DisplayName("Restores the selection and the known account of the handshake, without proof")
	@Test
	void restoresTheHandshakeSelectionWithoutProof() {
		lenient().when(providerManager.getProviders()).thenReturn(List.of(provider("alpha"), provider("beta")));
		DefaultProviderSelector selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));
		AccountProviderLink link = AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId("beta")
				.providerSubject("beta-subject")
				.build();
		PrepareContextItem handshake = new PrepareContextItem();
		handshake.setSelection(selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.verified("alpha", "alpha-proof"),
				ProviderClaim.of("beta", ProviderClaimOrigin.ENTRYPOINT)
		)));
		handshake.setPreferredLink(link);
		handshake.setJourneyMode(JourneyMode.SEAMLESS);
		when(prepareStateStore.peek(CONNECTION_KEY)).thenReturn(Optional.of(PrepareDecision.allow(handshake, null, "Alice")));

		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey(CONNECTION_KEY)
				.stage(PrepareStage.PROFILE)
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.build());
		PipelineState state = PipelineState.initial();
		new RestorePrepareStatePhase(prepareStateStore, selector).execute(state, groupState).toCompletableFuture().join();

		PrepareContextItem restored = state.get(PrepareContextItem.KEY).orElseThrow();
		assertEquals("beta", restored.getSelection().getWinnerProviderId());
		assertTrue(restored.getSelection().findVerifiedSubject("alpha").isEmpty());
		assertEquals(link, restored.getPreferredLink());
		assertEquals(JourneyMode.SEAMLESS, restored.getJourneyMode());
	}

	private static InternalProvider provider(String id) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		return InternalProvider.builder()
				.descriptor(descriptor)
				.state(ProviderState.ENABLED)
				.build();
	}
}
