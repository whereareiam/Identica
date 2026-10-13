package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Collect Provider Claims Phase")
class CollectProviderClaimsPhaseTest {
	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private ProviderManager providerManager;

	private final Set<ProviderClaimSource> sources = new LinkedHashSet<>();
	private final List<ProviderClaimRequest> requests = new ArrayList<>();
	private DefaultProviderSelector selector;

	@BeforeEach
	void setUp() {
		lenient().when(providerManager.getProviders()).thenReturn(List.of(provider("alpha"), provider("beta")));
		selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));
	}

	@DisplayName("Ranks the claims of every source into the selection, with the subjects resolved once")
	@Test
	void ranksTheClaimsOfEverySource() {
		List<SubjectResolution> subjects = List.of(SubjectResolution.builder()
				.providerId("alpha")
				.providerSubject("alpha-subject")
				.build());
		when(providerOperations.resolveSubjects(any())).thenReturn(subjects);
		source(ProviderClaim.of("alpha", ProviderClaimOrigin.DISCOVERY));
		source(ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME));

		PipelineState state = run(PrepareStage.HANDSHAKE, ProviderSelection.empty());

		ProviderSelection selection = state.get(PrepareContextItem.KEY).orElseThrow().getSelection();
		assertEquals("beta", selection.getWinnerProviderId());
		assertEquals(ProviderClaimOrigin.HOSTNAME, selection.getOrigin());
		assertEquals(2, requests.size());
		assertEquals(subjects, requests.getFirst().getSubjects());
	}

	@DisplayName("Adds the claims of the profile stage to the selection of the handshake")
	@Test
	void addsToTheSelectionOfTheHandshake() {
		source(ProviderClaim.verified("alpha", "alpha-proof"));
		ProviderSelection handshake = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("beta", ProviderClaimOrigin.NAME_MATCH)
		));

		PipelineState state = run(PrepareStage.PROFILE, handshake);

		ProviderSelection selection = state.get(PrepareContextItem.KEY).orElseThrow().getSelection();
		assertEquals("alpha", selection.getWinnerProviderId());
		assertTrue(selection.hasClaim("beta", ProviderClaimOrigin.NAME_MATCH));
		assertEquals(PrepareStage.PROFILE, requests.getFirst().getStage());
		assertEquals(handshake, requests.getFirst().getSelection());
	}

	@DisplayName("Keeps the claims of the other sources when one source fails")
	@Test
	void keepsTheOtherClaimsWhenASourceFails() {
		sources.add(request -> {
			throw new IllegalStateException("broken source");
		});
		source(ProviderClaim.of("alpha", ProviderClaimOrigin.NAME_MATCH));

		PipelineState state = run(PrepareStage.HANDSHAKE, ProviderSelection.empty());

		assertEquals("alpha", state.get(PrepareContextItem.KEY).orElseThrow().getSelection().getWinnerProviderId());
	}

	private void source(ProviderClaim claim) {
		sources.add(request -> {
			requests.add(request);
			return List.of(claim);
		});
	}

	private PipelineState run(PrepareStage stage, ProviderSelection selection) {
		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey("Alice|127.0.0.1||")
				.stage(stage)
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.build());
		PrepareContextItem context = new PrepareContextItem();
		context.setSelection(selection);
		PipelineState state = PipelineState.initial();
		state.put(PrepareContextItem.KEY, context);

		new CollectProviderClaimsPhase(registry(), selector, providerOperations)
				.execute(state, groupState)
				.toCompletableFuture()
				.join();

		return state;
	}

	private Registry<ProviderClaimSource> registry() {
		return new Registry<>() {
			@Override
			public void register(ProviderClaimSource value) {
				sources.add(value);
			}

			@Override
			public void unregister(ProviderClaimSource value) {
				sources.remove(value);
			}

			@Override
			public Set<ProviderClaimSource> values() {
				return sources;
			}
		};
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
