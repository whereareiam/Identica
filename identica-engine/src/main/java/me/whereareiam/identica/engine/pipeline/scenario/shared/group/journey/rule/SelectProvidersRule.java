package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.rule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
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
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
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
		String pendingProviderId = resolvePendingProviderId(context, pipelineType);
		String preferredProviderId = resolvePreferredProviderId(context, pipelineType);
		Set<String> excludedProviderIds = resolveExcludedProviderIds(ctx);
		List<String> orderedProviderIds = orderedProviderIds(
				eligibleProviders,
				pendingProviderId,
				preferredProviderId,
				excludedProviderIds,
				pipelineType
		);

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

	private @NotNull List<String> orderedProviderIds(
			@NotNull List<InternalProvider> eligibleProviders,
			@Nullable String pendingProviderId,
			@Nullable String preferredProviderId,
			@NotNull Set<String> excludedProviderIds,
			@NotNull PipelineType pipelineType
	) {
		List<String> providerIds = new ArrayList<>();
		for (InternalProvider provider : eligibleProviders) {
			String id = providerId(provider);
			if (id.isBlank())
				continue;
			if (indexOfProvider(providerIds, id) < 0)
				providerIds.add(id);
		}

		if (!excludedProviderIds.isEmpty()) {
			providerIds.removeIf(id -> excludedProviderIds.contains(normalizeProviderId(id)));
		}

		if (pipelineType == PipelineType.MIGRATION) {
			if (pendingProviderId == null || pendingProviderId.isBlank())
				return List.of();
			int existingIndex = indexOfProvider(providerIds, pendingProviderId);
			return existingIndex >= 0 ? List.of(providerIds.get(existingIndex)) : List.of();
		}

		if (preferredProviderId != null && !preferredProviderId.isBlank()) {
			int preferredIndex = indexOfProvider(providerIds, preferredProviderId);
			if (preferredIndex >= 0) {
				String preferred = providerIds.remove(preferredIndex);
				providerIds.addFirst(preferred);
			}
		}

		if (pendingProviderId != null && !pendingProviderId.isBlank()) {
			int existingIndex = indexOfProvider(providerIds, pendingProviderId);
			if (existingIndex >= 0) providerIds.remove(existingIndex);
			providerIds.addFirst(pendingProviderId);
		}

		return providerIds;
	}

	private int indexOfProvider(@NotNull List<String> providerIds, @NotNull String pendingProviderId) {
		for (int index = 0; index < providerIds.size(); index++) {
			String candidate = providerIds.get(index);
			if (candidate != null && candidate.equalsIgnoreCase(pendingProviderId))
				return index;
		}
		return -1;
	}

	private @Nullable String resolvePreferredProviderId(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType
	) {
		if (pipelineType != PipelineType.AUTHENTICATION) return null;
		if (context.getAccountUniqueId() == null) return null;

		return providerLinkPersistenceService.findByUniqueId(context.getAccountUniqueId()).stream()
				.filter(AccountProviderLink::isPrimaryLink)
				.map(AccountProviderLink::getProviderId)
				.findFirst()
				.orElse(null);
	}

	private @Nullable String resolvePendingProviderId(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType
	) {
		if (pipelineType == PipelineType.MIGRATION && context instanceof MigrationContext migrationContext) {
			String targetProviderId = migrationContext.getTargetProviderId();
			if (targetProviderId != null && !targetProviderId.isBlank())
				return targetProviderId;
		}

		if (context.getProvider() == null)
			return null;

		String providerId = context.getProvider().getProviderId();
		return providerId != null && !providerId.isBlank() ? providerId : null;
	}

	private @NotNull String providerId(@Nullable InternalProvider provider) {
		if (provider == null || provider.getDescriptor() == null)
			return "";

		return provider.getDescriptor().getId();
	}

	private @NotNull Set<String> resolveExcludedProviderIds(@NotNull JourneyRuleContext ctx) {
		PipelineState state = ctx.getPipelineState();
		if (state != null) {
			JourneyOverrideItem override = state.item(JourneyOverrideItem.class).orElse(null);
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

		JourneyOverrideItem override = stored.item(JourneyOverrideItem.class).orElse(null);
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
