package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.rule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option.JourneyOption;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option.JourneyOptions;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.JourneyRuleContext;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionBlock;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionStage;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.registry.JourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.rule.JourneyRule;
import me.whereareiam.identica.pipeline.journey.rule.JourneyRuleScope;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the provider stage of a journey with one block per option, in the order {@link JourneyOptions} tries
 * them. When an option cannot get the scenario done, the journey falls back to the next block.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SelectProvidersRule implements JourneyRule {
	private final JourneyOptions journeyOptions;
	private final AuthenticationJourneyRegistry authenticationJourneyRegistry;
	private final RegistrationJourneyRegistry registrationJourneyRegistry;
	private final MigrationJourneyRegistry migrationJourneyRegistry;
	private final PipelineStateStore pipelineStateStore;

	@Override
	public @NotNull String id() {
		return "select-providers";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull JourneyRuleScope scope() {
		return JourneyRuleScope.forStageType(StageType.PROVIDER);
	}

	@Override
	public @NotNull JourneyExecutionPlan apply(
			@NotNull JourneyRuleContext ctx,
			@NotNull JourneyExecutionPlan current
	) {
		if (current.blocks().isEmpty())
			return current;

		ScenarioContext context = ctx.getContext();
		PipelineType pipelineType = ctx.getPipelineType();
		List<JourneyOption> options = journeyOptions.resolve(context, pipelineType, resolveOverride(ctx));
		if (options.isEmpty())
			return current;

		JourneyRegistry registry = resolveRegistry(pipelineType);
		List<JourneyExecutionBlock> resolvedBlocks = new ArrayList<>();
		for (JourneyOption option : options) {
			JourneyPlan optionPlan = registry.resolvePlan(context, pipelineType, option.getProviderId(), option.methodName());

			List<JourneyExecutionStage> providerStages = new ArrayList<>();
			for (JourneyPlan.StageEntry entry : optionPlan.stages()) {
				if (entry == null) continue;
				if (!entry.stage().providerStage()) continue;

				providerStages.add(new JourneyExecutionStage(entry.stage(), entry.steps()));
			}

			if (providerStages.isEmpty()) continue;
			resolvedBlocks.add(new JourneyExecutionBlock(
					id(),
					JourneyExecutionPolicy.FALLBACK,
					option.getProviderId(),
					option.methodName(),
					providerStages
			));
		}

		if (resolvedBlocks.isEmpty()) return current;

		return new JourneyExecutionPlan(resolvedBlocks);
	}

	private @NotNull JourneyRegistry resolveRegistry(@NotNull PipelineType pipelineType) {
		if (pipelineType == PipelineType.REGISTRATION)
			return registrationJourneyRegistry;
		if (pipelineType == PipelineType.MIGRATION)
			return migrationJourneyRegistry;
		return authenticationJourneyRegistry;
	}

	/**
	 * The providers and methods the journey excluded: from the state of the run, or from the stored state of the
	 * connection's pending journey when the rule runs outside a run.
	 */
	private @Nullable JourneyOverrideItem resolveOverride(@NotNull JourneyRuleContext ctx) {
		PipelineState state = ctx.getPipelineState();
		if (state != null)
			return state.get(JourneyOverrideItem.KEY).orElse(null);

		PipelineStateReference reference = PipelineStateReference.from(ctx.getContext());
		if (reference.isEmpty())
			return null;

		return pipelineStateStore.find(reference)
				.flatMap(stored -> stored.get(JourneyOverrideItem.KEY))
				.orElse(null);
	}
}
