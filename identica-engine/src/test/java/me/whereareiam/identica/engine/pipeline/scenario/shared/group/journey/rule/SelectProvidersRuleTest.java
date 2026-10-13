package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.rule;

import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.JourneyRuleContext;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionBlock;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionStage;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Select Providers Rule")
class SelectProvidersRuleTest {
	@Test
	@DisplayName("Migration selection uses the target provider plan without capability metadata")
	void migrationSelectionUsesTargetProviderPlan() {
		ProviderOperations providerOperations = mock(ProviderOperations.class);
		AuthenticationJourneyRegistry authenticationJourneyRegistry = mock(AuthenticationJourneyRegistry.class);
		RegistrationJourneyRegistry registrationJourneyRegistry = mock(RegistrationJourneyRegistry.class);
		MigrationJourneyRegistry migrationJourneyRegistry = mock(MigrationJourneyRegistry.class);
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);

		InternalProvider provider = provider("premium");
		MigrationContext context = MigrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build();
		when(providerOperations.eligibleProviders(context, PipelineType.MIGRATION, JourneyMode.SEAMLESS))
				.thenReturn(List.of(provider));
		when(migrationJourneyRegistry.resolvePlan(context, PipelineType.MIGRATION, JourneyMode.SEAMLESS, "premium"))
				.thenReturn(providerPlan("premium"));

		SelectProvidersRule rule = new SelectProvidersRule(
				providerOperations,
				authenticationJourneyRegistry,
				registrationJourneyRegistry,
				migrationJourneyRegistry,
				new DefaultProviderSelector(new ProviderClaimRanking(mock(ProviderManager.class))),
				pipelineStateStore
		);

		JourneyExecutionPlan result = rule.apply(
				new JourneyRuleContext(context, PipelineType.MIGRATION, JourneyMode.SEAMLESS, null, null),
				seedPlan()
		);

		assertEquals(1, result.blocks().size());
		assertEquals("premium", result.blocks().getFirst().providerId());
		assertEquals(1, result.blocks().getFirst().stages().size());
	}

	@Test
	@DisplayName("Orders eligible providers by the connection's selection, then by priority")
	void ordersEligibleProvidersBySelection() {
		ProviderOperations providerOperations = mock(ProviderOperations.class);
		RegistrationJourneyRegistry registrationJourneyRegistry = mock(RegistrationJourneyRegistry.class);
		ProviderManager providerManager = mock(ProviderManager.class);
		List<InternalProvider> providers = List.of(provider("alpha", 30), provider("beta", 20), provider("gamma", 10));
		when(providerManager.getProviders()).thenReturn(providers);
		DefaultProviderSelector selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));

		RegistrationContext context = RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.build();
		context.setSelection(selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("gamma", ProviderClaimOrigin.HOSTNAME),
				ProviderClaim.of("alpha", ProviderClaimOrigin.DISCOVERY),
				ProviderClaim.of("delta", ProviderClaimOrigin.MIGRATION)
		)));
		when(providerOperations.eligibleProviders(context, PipelineType.REGISTRATION, JourneyMode.SEAMLESS))
				.thenReturn(providers);
		for (InternalProvider provider : providers) {
			String id = provider.getDescriptor().getId();
			when(registrationJourneyRegistry.resolvePlan(context, PipelineType.REGISTRATION, JourneyMode.SEAMLESS, id))
					.thenReturn(providerPlan(id));
		}

		SelectProvidersRule rule = new SelectProvidersRule(
				providerOperations,
				mock(AuthenticationJourneyRegistry.class),
				registrationJourneyRegistry,
				mock(MigrationJourneyRegistry.class),
				selector,
				mock(PipelineStateStore.class)
		);

		JourneyExecutionPlan result = rule.apply(
				new JourneyRuleContext(context, PipelineType.REGISTRATION, JourneyMode.SEAMLESS, null, PipelineState.initial()),
				seedPlan()
		);

		assertEquals(
				List.of("gamma", "alpha", "beta"),
				result.blocks().stream().map(JourneyExecutionBlock::providerId).toList()
		);
	}

	private InternalProvider provider(String id, int priority) {
		InternalProvider provider = provider(id);
		provider.setPriority(priority);
		return provider;
	}

	private InternalProvider provider(String id) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setName(id);
		descriptor.setVersion("1.0.0");
		descriptor.setMain("ignored.Main");
		descriptor.setSupportedPlatforms(List.of("ANY"));
		return InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build();
	}

	private JourneyExecutionPlan seedPlan() {
		JourneyStage stage = providerStage();
		return new JourneyExecutionPlan(List.of(new JourneyExecutionBlock(
				"seed",
				JourneyExecutionPolicy.SEQUENTIAL,
				null,
				List.of(new JourneyExecutionStage(stage, List.of()))
		)));
	}

	private JourneyPlan providerPlan(String providerId) {
		return new JourneyPlan(List.of(new JourneyPlan.StageEntry(
				providerStage(),
				List.of(JourneyStep.builder()
						.stageId(StageType.PROVIDER.id())
						.providerId(providerId)
						.scenarios(EnumSet.of(PipelineType.MIGRATION))
						.journeyModes(EnumSet.of(JourneyMode.SEAMLESS))
						.step(new Step() {
							@Override
							public @NotNull String getName() {
								return "migration-step";
							}

							@Override
							public @NotNull Set<JourneyMode> journeyModes() {
								return Set.of(JourneyMode.SEAMLESS);
							}

							@Override
							public @NotNull StepContextRequirement contextRequirement() {
								return StepContextRequirement.LOGIN;
							}

							@Override
							public @NotNull CompletableFuture<StepResult> execute(@NotNull me.whereareiam.identica.pipeline.ScenarioContext context) {
								return CompletableFuture.completedFuture(null);
							}
						})
						.build())
		)));
	}

	private JourneyStage providerStage() {
		return JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(100)
				.pipelineTypes(EnumSet.of(PipelineType.MIGRATION))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.build();
	}
}
