package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.rule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.JourneyRuleContext;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionBlock;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionStage;
import me.whereareiam.identica.model.provider.InternalProvider;
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
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SelectProvidersRule implements JourneyRule {
	private final ProviderOperations providerOperations;
	private final AuthenticationJourneyRegistry authenticationJourneyRegistry;
	private final RegistrationJourneyRegistry registrationJourneyRegistry;
	private final MigrationJourneyRegistry migrationJourneyRegistry;
	private final ProviderSelector providerSelector;
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
		JourneyMode journeyMode = ctx.getJourneyMode();

		List<InternalProvider> eligibleProviders = new ArrayList<>(
				providerOperations.eligibleProviders(context, pipelineType, journeyMode)
		);
		Set<String> excludedProviderIds = resolveExcludedProviderIds(ctx);
		List<String> orderedProviderIds = orderedProviderIds(context, eligibleProviders, excludedProviderIds, pipelineType);

		if (orderedProviderIds.isEmpty())
			return current;

		JourneyRegistry registry = resolveRegistry(pipelineType);
		List<JourneyExecutionBlock> resolvedBlocks = new ArrayList<>();
		for (String providerId : orderedProviderIds) {
			if (providerId == null || providerId.isBlank()) continue;
			JourneyPlan providerPlan = registry.resolvePlan(context, pipelineType, journeyMode, providerId);

			List<JourneyExecutionStage> providerStages = new ArrayList<>();
			for (JourneyPlan.StageEntry entry : providerPlan.stages()) {
				if (entry == null) continue;
				if (!entry.stage().providerStage()) continue;

				providerStages.add(new JourneyExecutionStage(entry.stage(), entry.steps()));
			}

			if (providerStages.isEmpty()) continue;
			resolvedBlocks.add(new JourneyExecutionBlock(
					id(),
					JourneyExecutionPolicy.FALLBACK,
					providerId,
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
	 * Orders the eligible providers by the connection's selection. A migration only runs its target.
	 */
	private @NotNull List<String> orderedProviderIds(
			@NotNull ScenarioContext context,
			@NotNull List<InternalProvider> eligibleProviders,
			@NotNull Set<String> excludedProviderIds,
			@NotNull PipelineType pipelineType
	) {
		List<String> providerIds = eligibleProviders.stream()
				.map(this::providerId)
				.filter(id -> !id.isBlank())
				.filter(id -> !excludedProviderIds.contains(normalizeProviderId(id)))
				.toList();

		if (pipelineType == PipelineType.MIGRATION) return migrationTarget(context, providerIds);

		return providerSelector.rank(context.getSelection(), providerIds);
	}

	private @NotNull List<String> migrationTarget(@NotNull ScenarioContext context, @NotNull List<String> providerIds) {
		if (!(context instanceof MigrationContext migration) || migration.getTargetProviderId() == null)
			return List.of();

		String targetProviderId = migration.getTargetProviderId();
		return providerIds.stream()
				.filter(targetProviderId::equalsIgnoreCase)
				.limit(1)
				.toList();
	}

	private @NotNull String providerId(@Nullable InternalProvider provider) {
		if (provider == null || provider.getDescriptor() == null)
			return "";

		return provider.getDescriptor().getId();
	}

	private @NotNull Set<String> resolveExcludedProviderIds(@NotNull JourneyRuleContext ctx) {
		PipelineState state = ctx.getPipelineState();
		if (state != null) {
			JourneyOverrideItem override = state.get(JourneyOverrideItem.KEY).orElse(null);
			if (override == null || override.getExcludedProviders() == null || override.getExcludedProviders().isEmpty())
				return Set.of();

			return normalizeExcluded(override.getExcludedProviders());
		}

		PipelineStateReference reference = PipelineStateReference.from(ctx.getContext());
		if (reference.isEmpty())
			return Set.of();

		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null)
			return Set.of();

		JourneyOverrideItem override = stored.get(JourneyOverrideItem.KEY).orElse(null);
		if (override == null || override.getExcludedProviders() == null || override.getExcludedProviders().isEmpty())
			return Set.of();

		return normalizeExcluded(override.getExcludedProviders());
	}

	private @NotNull Set<String> normalizeExcluded(@NotNull List<String> excluded) {
		Set<String> normalized = new HashSet<>();
		for (String providerId : excluded) {
			String normalizedId = normalizeProviderId(providerId);
			if (normalizedId != null)
				normalized.add(normalizedId);
		}
		return normalized;
	}

	private @Nullable String normalizeProviderId(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank())
			return null;
		return providerId.trim().toLowerCase(Locale.ROOT);
	}
}
