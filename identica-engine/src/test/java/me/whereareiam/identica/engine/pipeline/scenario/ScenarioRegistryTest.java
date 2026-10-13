package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.AuthenticationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.MigrationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.RegistrationPipeline;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Scenario Registry")
class ScenarioRegistryTest {
	@Mock
	private PipelineStateStore pipelineStateStore;
	@Mock
	private AuthenticationPipeline authenticationPipeline;
	@Mock
	private RegistrationPipeline registrationPipeline;
	@Mock
	private MigrationPipeline migrationPipeline;
	@Mock
	private ProviderManager providerManager;

	private ProviderSelector selector;
	private ScenarioRegistry registry;

	@BeforeEach
	void setUp() {
		when(authenticationPipeline.type()).thenReturn(PipelineType.AUTHENTICATION);
		when(registrationPipeline.type()).thenReturn(PipelineType.REGISTRATION);
		when(migrationPipeline.type()).thenReturn(PipelineType.MIGRATION);
		when(registrationPipeline.isPending(any())).thenReturn(true);
		when(registrationPipeline.matchesNewScenario(any())).thenReturn(true);
		when(providerManager.getProviders()).thenReturn(List.of(provider("alpha", 10), provider("beta", 20)));

		selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));
		registry = new ScenarioRegistry(
				pipelineStateStore,
				selector,
				authenticationPipeline,
				registrationPipeline,
				migrationPipeline
		);
	}

	@DisplayName("Resumes a pending pipeline of the provider that wins the arriving connection")
	@Test
	void resumesAPendingPipelineOfTheWinningProvider() {
		pending("alpha", ProviderSelection.empty());

		ScenarioSelection selection = registry.select(request(ProviderClaim.of("alpha", ProviderClaimOrigin.DISCOVERY)));

		assertResumed(selection);
	}

	@DisplayName("Discards a pending pipeline when another provider proves the arriving connection")
	@Test
	void discardsAPendingPipelineWhenAnotherProviderWins() {
		pending("alpha", ProviderSelection.empty());

		ScenarioSelection selection = registry.select(request(
				ProviderClaim.of("alpha", ProviderClaimOrigin.DISCOVERY),
				ProviderClaim.verified("beta", "beta-proof")
		));

		assertFalse(selection.isResume());
		verify(pipelineStateStore).clear(any(PipelineStateReference.class));
	}

	@DisplayName("Resumes the pending pipeline the player chose a provider in, over the discovery of another")
	@Test
	void resumesThePlayersChoiceOverDiscovery() {
		pending("alpha", ProviderSelection.empty());

		ScenarioSelection selection = registry.select(request(
				ProviderClaim.of("alpha", ProviderClaimOrigin.PLAYER_CHOICE),
				ProviderClaim.of("beta", ProviderClaimOrigin.DISCOVERY)
		));

		assertResumed(selection);
	}

	@DisplayName("Keeps the providers the pending pipeline excluded out of the arriving ranking")
	@Test
	void keepsTheExclusionsOfThePendingPipeline() {
		pending("alpha", selector.exclude(ProviderSelection.empty(), "beta"));

		ScenarioSelection selection = registry.select(request(
				ProviderClaim.of("alpha", ProviderClaimOrigin.DISCOVERY),
				ProviderClaim.of("beta", ProviderClaimOrigin.DISCOVERY)
		));

		assertResumed(selection);
	}

	@DisplayName("Resumes a pending pipeline when nothing claims the arriving connection")
	@Test
	void resumesWhenNothingClaimsTheConnection() {
		pending("alpha", ProviderSelection.empty());

		ScenarioSelection selection = registry.select(request());

		assertResumed(selection);
	}

	@DisplayName("Resumes a pending pipeline that runs no provider yet")
	@Test
	void resumesWhenThePendingPipelineRunsNoProvider() {
		pending(null, ProviderSelection.empty());

		ScenarioSelection selection = registry.select(request(ProviderClaim.verified("beta", "beta-proof")));

		assertResumed(selection);
	}

	private void assertResumed(ScenarioSelection selection) {
		assertTrue(selection.isResume());
		assertSame(registrationPipeline, selection.getRunner());
		verify(pipelineStateStore, never()).clear(any(PipelineStateReference.class));
	}

	private void pending(String providerId, ProviderSelection selection) {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(RegistrationContext.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.provider(ProviderContext.of(providerId, providerId == null ? null : providerId + "-subject", "Alice"))
				.selection(selection)
				.build());
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.of(state));
	}

	private ConnectionRequest request(ProviderClaim... claims) {
		return ConnectionRequest.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.selection(selector.claim(ProviderSelection.empty(), List.of(claims)))
				.build();
	}

	private static InternalProvider provider(String id, int priority) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		return InternalProvider.builder()
				.descriptor(descriptor)
				.priority(priority)
				.state(ProviderState.ENABLED)
				.build();
	}
}
