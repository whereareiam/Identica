package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolve Profile Phase")
class ResolveProfilePhaseTest {
	private static final String USERNAME = "PlayerOne";

	@Mock
	private ProviderOperations providerOperations;

	@DisplayName("Continues with the subject the winning provider verified")
	@Test
	void continuesWithTheVerifiedSubject() {
		PipelineState state = run(ProviderClaim.verified("alpha", "alpha-proof"));

		ProviderContext provider = provider(state);
		assertEquals("alpha", provider.getProviderId());
		assertEquals("alpha-proof", provider.getProviderSubject());
		verify(providerOperations, never()).resolveSelectedSubject(any(), any());
	}

	@DisplayName("Continues with the subject the winning provider resolves when it proved nothing")
	@Test
	void continuesWithTheResolvedSubjectOfTheWinner() {
		when(providerOperations.resolveSelectedSubject(eq("beta"), any())).thenReturn(SubjectResolution.builder()
				.providerId("beta")
				.providerSubject("beta-subject")
				.build());

		PipelineState state = run(ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME));

		ProviderContext provider = provider(state);
		assertEquals("beta", provider.getProviderId());
		assertEquals("beta-subject", provider.getProviderSubject());
		assertEquals(USERNAME, provider.getProviderUsername());
	}

	@DisplayName("Continues with the winning provider without a subject when it resolves none")
	@Test
	void continuesWithoutASubjectWhenTheWinnerResolvesNone() {
		PipelineState state = run(ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME));

		ProviderContext provider = provider(state);
		assertEquals("beta", provider.getProviderId());
		assertNull(provider.getProviderSubject());
		assertTrue(state.get(PrepareDecision.KEY).isEmpty());
	}

	@DisplayName("Defers the profile when nothing claims the connection")
	@Test
	void defersWhenNothingClaimsTheConnection() {
		PipelineState state = run(null);

		assertNull(state.get(PrepareContextItem.KEY).orElseThrow().getProvider());
		assertTrue(state.get(PrepareDecision.KEY).isPresent());
	}

	private PipelineState run(ProviderClaim winner) {
		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey("PlayerOne|127.0.0.1||")
				.stage(PrepareStage.PROFILE)
				.identity(new ConnectionIdentity(USERNAME, "127.0.0.1"))
				.build());

		PrepareContextItem context = new PrepareContextItem();
		context.setSelection(ProviderSelection.of(winner, List.of(), List.of()));
		PipelineState state = PipelineState.initial();
		state.put(PrepareContextItem.KEY, context);
		new ResolveProfilePhase(providerOperations)
				.execute(state, groupState)
				.toCompletableFuture()
				.join();

		return state;
	}

	private static ProviderContext provider(PipelineState state) {
		ProviderContext provider = state.get(PrepareContextItem.KEY).orElseThrow().getProvider();
		assertNotNull(provider);
		return provider;
	}
}
