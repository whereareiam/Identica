package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.pipeline.scenario.shared.ProviderSelectedEvent;
import me.whereareiam.identica.event.step.StepFinishedEvent;
import me.whereareiam.identica.event.step.StepPrepareEvent;
import me.whereareiam.identica.event.step.StepStartedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionBlock;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionPlan;
import me.whereareiam.identica.model.pipeline.journey.execution.JourneyExecutionStage;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.JourneyState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import me.whereareiam.identica.type.pipeline.journey.step.StepWaitReason;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExecutePlanPhase implements PipelinePhase<JourneyState> {
	private final IdentityService identityService;
	private final EventManager eventManager;
	private final Provider<Engine> engineProvider;
	private final Provider<Messages> messagesProvider;
	private final ProviderManager providerManager;
	private final ProviderOperations providerOperations;
	private final RoutingCoordinator routingCoordinator;

	@Override
	public @NotNull String id() {
		return "execute-plan";
	}

	@Override
	public int order() {
		return 500;
	}

	@Override
	public @NotNull Class<JourneyState> stateType() {
		return JourneyState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<JourneyState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull JourneyState state
	) {
		if (state.getResult() != null) {
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ScenarioContext context = state.getContext();
		PipelineType pipelineType = pipelineState.getPipelineType();
		JourneyMode journeyMode = state.getJourneyMode();
		JourneyExecutionPlan plan = state.getExecutionPlan();
		if (context == null || pipelineType == null || journeyMode == null) {
			state.setResult(PipelineResult.failed(journeyMissingContextMessage(pipelineState)));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (plan == null) {
			state.setResult(PipelineResult.failed(journeyMissingPlanMessage(pipelineState)));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		JourneyStateItem pending = state.getPending();
		PipelineResult transitionResult = applyTransitionContract(pipelineState, context, pipelineType);
		if (transitionResult != null) {
			state.setResult(transitionResult);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		PipelineResult result = executePlan(pipelineState, context, pipelineType, journeyMode, pending, plan);
		state.setResult(result != null ? result : PipelineResult.complete());
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @Nullable PipelineResult applyTransitionContract(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType
	) {
		ScenarioTransitionItem resolved = context.getTransition();
		if (resolved == null || resolved.getTargetPipeline() != pipelineType)
			return null;

		applyProviderPolicy(context, resolved.getProviderPolicy());

		if (resolved.isConsumeOnce()) context.setTransition(null);
		pipelineState.setScenario(context);

		return resolved.getJourneyPolicy() == ScenarioTransitionItem.JourneyPolicy.SKIP
				? PipelineResult.complete()
				: null;
	}

	private void applyProviderPolicy(
			@NotNull ScenarioContext context,
			@Nullable ScenarioTransitionItem.ProviderPolicy providerPolicy
	) {
		if (providerPolicy == ScenarioTransitionItem.ProviderPolicy.CLEAR
				|| providerPolicy == ScenarioTransitionItem.ProviderPolicy.RESELECT) {
			clearProvider(context);
		}
	}

	private @Nullable PipelineResult executePlan(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode,
			@Nullable JourneyStateItem pending,
			@NotNull JourneyExecutionPlan plan
	) {
		Set<String> excludedProviders = new HashSet<>(loadExcludedProviders(pipelineState));
		// Denials issued because a step needed input, kept by identity: their text is configurable
		// and may be empty or equal to another denial's, so it cannot tell them apart.
		Set<PipelineResult> interactionDenials = Collections.newSetFromMap(new IdentityHashMap<>());
		PipelineResult interactionDenial = null;

		restart:
		while (true) {
			List<JourneyExecutionBlock> blocks = plan.blocks();
			String pendingProviderId = resolvePendingProviderId(context, pending);
			int startIndex = resolveStartIndex(blocks, pending, pendingProviderId, context);

			for (int index = startIndex; index < blocks.size(); index++) {
				JourneyExecutionBlock block = blocks.get(index);
				if (block == null) continue;

				if (block.policy() == JourneyExecutionPolicy.FALLBACK) {
					BlockRegion region = collectFallbackRegion(blocks, index);
					FallbackOutcome fallbackResult = executeFallbackBlocks(
							pipelineState,
							context,
							pipelineType,
							journeyMode,
							pending,
							pendingProviderId,
							region.blocks(),
							interactionDenials
					);
					if (fallbackResult != null && fallbackResult.result != null) {
						if (interactionDenials.contains(fallbackResult.result))
							interactionDenial = fallbackResult.result;

						String failedProviderId = normalizeProviderId(fallbackResult.failedProviderId);
						if (failedProviderId != null && excludedProviders.add(failedProviderId)) {
							recordExcludedProviders(pipelineState, context, pipelineType, excludedProviders);
							JourneyExecutionPlan updatedPlan = removeExcludedProviders(plan, excludedProviders);
							// Another provider may still sign the player in without input. Once none is left, the
							// player learns that input was the obstacle instead of continuing without a provider.
							if (interactionDenial != null && !hasProviderBlock(updatedPlan))
								return interactionDenial;

							if (!updatedPlan.equals(plan) && !updatedPlan.blocks().isEmpty()) {
								plan = updatedPlan;
								pending = null;
								clearProvider(context);
								pipelineState.setScenario(context);
								continue restart;
							}
						}
						return fallbackResult.result;
					}

					context = currentScenario(pipelineState, pipelineType, context);
					index = region.endIndex();
					continue;
				}

				PipelineResult blockResult = executeBlock(
						pipelineState,
						context,
						pipelineType,
						journeyMode,
						pending,
						pendingProviderId,
						block,
						interactionDenials
				);
				if (blockResult != null)
					return blockResult;

				context = currentScenario(pipelineState, pipelineType, context);
			}

			return null;
		}
	}

	private @Nullable FallbackOutcome executeFallbackBlocks(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode,
			@Nullable JourneyStateItem pending,
			@Nullable String pendingProviderId,
			@NotNull List<JourneyExecutionBlock> blocks,
			@NotNull Set<PipelineResult> interactionDenials
	) {
		boolean anySuccess = false;

		for (JourneyExecutionBlock block : blocks) {
			if (block == null) continue;
			context = currentScenario(pipelineState, pipelineType, context);

			PipelineResult blockResult = executeBlock(
					pipelineState,
					context,
					pipelineType,
					journeyMode,
					pending,
					pendingProviderId,
					block,
					interactionDenials
			);
			if (blockResult == null) {
				anySuccess = true;
				break;
			}

			PipelineStatus status = blockResult.getStatus();
			if (status == PipelineStatus.WAITING || status == PipelineStatus.REQUIRE_RECONNECT)
				return new FallbackOutcome(blockResult, null);

			if (status == PipelineStatus.COMPLETE) {
				anySuccess = true;
				break;
			}

			if (status == PipelineStatus.FAILED
					|| status == PipelineStatus.DENIED
					|| status == PipelineStatus.NO_PENDING) {
				if (pipelineType == PipelineType.MIGRATION)
					return new FallbackOutcome(blockResult, null);

				return new FallbackOutcome(blockResult, block.providerId());
			}
		}

		if (!anySuccess)
			return new FallbackOutcome(PipelineResult.failed(journeyNoCompletionMessage()), null);

		return null;
	}

	private @Nullable PipelineResult executeBlock(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode,
			@Nullable JourneyStateItem pending,
			@Nullable String pendingProviderId,
			@NotNull JourneyExecutionBlock block,
			@NotNull Set<PipelineResult> interactionDenials
	) {
		String effectiveProviderId = resolveEffectiveProviderId(block, context);
		String blockProviderId = block.providerId();
		if (blockProviderId != null && !blockProviderId.isBlank()) {
			applyProviderContext(context, blockProviderId);
		}

		InternalProvider provider = resolveProvider(effectiveProviderId);
		if (blockProviderId != null && provider != null) {
			eventManager.call(new ProviderSelectedEvent(context, provider, journeyMode));
		}

		return executeStageEntries(
				pipelineState,
				context,
				pipelineType,
				journeyMode,
				pending,
				pendingProviderId,
				block.stages(),
				effectiveProviderId,
				provider,
				interactionDenials
		);
	}

	private void clearProvider(@NotNull ScenarioContext context) {
		ProviderContext provider = context.getProvider();
		if (provider != null) {
			provider.setProviderId("");
			provider.setProviderSubject("");
			provider.setProviderUsername("");
		}
		context.setProvider(null);
	}

	private @NotNull Set<String> loadExcludedProviders(@NotNull PipelineState pipelineState) {
		JourneyOverrideItem override = pipelineState.item(JourneyOverrideItem.class).orElse(null);
		if (override == null || override.getExcludedProviders() == null || override.getExcludedProviders().isEmpty())
			return Set.of();

		Set<String> normalized = new HashSet<>();
		for (String providerId : override.getExcludedProviders()) {
			String normalizedId = normalizeProviderId(providerId);
			if (normalizedId != null)
				normalized.add(normalizedId);
		}
		return normalized;
	}

	private void recordExcludedProviders(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull Set<String> excludedProviders
	) {
		long ttlMs = scenarioSettings(pipelineType).pipelineTtlMillis();
		if (ttlMs <= 0)
			return;

		JourneyOverrideItem current = pipelineState.item(JourneyOverrideItem.class).orElse(null);
		List<String> existing = current != null && current.getExcludedProviders() != null
				? current.getExcludedProviders()
				: List.of();

		Set<String> merged = new HashSet<>();
		for (String providerId : existing) {
			String normalized = normalizeProviderId(providerId);
			if (normalized != null)
				merged.add(normalized);
		}
		merged.addAll(excludedProviders);

		JourneyOverrideItem updated = new JourneyOverrideItem(
				current != null ? current.getJourneyMode() : null,
				current != null ? current.getStageId() : null,
				current != null ? current.getStepIndex() : -1,
				current != null && current.isClearProvider(),
				current != null ? current.getProviderId() : null,
				List.copyOf(merged)
		);

		pipelineState.putItem(updated, ttlMs);
	}

	private @NotNull JourneyExecutionPlan removeExcludedProviders(
			@NotNull JourneyExecutionPlan plan,
			@NotNull Set<String> excludedProviders
	) {
		if (excludedProviders.isEmpty())
			return plan;

		List<JourneyExecutionBlock> filtered = new ArrayList<>();
		for (JourneyExecutionBlock block : plan.blocks()) {
			if (block == null) continue;
			String providerId = normalizeProviderId(block.providerId());
			if (providerId != null
					&& block.policy() == JourneyExecutionPolicy.FALLBACK
					&& excludedProviders.contains(providerId)) {
				continue;
			}
			filtered.add(block);
		}

		return new JourneyExecutionPlan(filtered);
	}

	private @Nullable String normalizeProviderId(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank())
			return null;
		return providerId.trim().toLowerCase(Locale.ROOT);
	}

	private static final class FallbackOutcome {
		private final @Nullable PipelineResult result;
		private final @Nullable String failedProviderId;

		private FallbackOutcome(@Nullable PipelineResult result, @Nullable String failedProviderId) {
			this.result = result;
			this.failedProviderId = failedProviderId;
		}
	}

	private @Nullable PipelineResult executeStageEntries(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode,
			@Nullable JourneyStateItem pending,
			@Nullable String pendingProviderId,
			@NotNull List<JourneyExecutionStage> stages,
			@Nullable String providerId,
			@Nullable InternalProvider provider,
			@NotNull Set<PipelineResult> interactionDenials
	) {
		int startStageIndex = 0;
		if (pending != null
				&& pending.getStageId() != null
				&& !pending.getStageId().isBlank()
				&& (providerId == null || providerId.isBlank() || matchesProvider(pendingProviderId, providerId))) {
			for (int index = 0; index < stages.size(); index++) {
				JourneyExecutionStage candidate = stages.get(index);
				if (candidate == null) continue;
				if (candidate.stage().getId().equalsIgnoreCase(pending.getStageId())) {
					startStageIndex = index;
					break;
				}
			}
		}

		for (int stageIndex = startStageIndex; stageIndex < stages.size(); stageIndex++) {
			JourneyExecutionStage entry = stages.get(stageIndex);
			if (entry == null) continue;

			JourneyStage stage = entry.stage();
			int startIndex = 0;
			if (pending != null
					&& pending.getStageId() != null
					&& pending.getStageId().equalsIgnoreCase(stage.getId())
					&& (providerId == null || providerId.isBlank() || matchesProvider(pendingProviderId, providerId))) {
				startIndex = Math.max(0, pending.getStepIndex());
			}

			boolean completedStage = false;
			List<JourneyStep> steps = entry.steps();
			for (int index = startIndex; index < steps.size(); index++) {
				JourneyStep journeyStep = steps.get(index);
				StepResult stepResult = executeStep(
						pipelineState,
						context,
						pipelineType,
						journeyMode,
						stage,
						journeyStep,
						provider
				);

				if (stepResult.getUpdatedContext() != null) {
					pipelineState.setScenario(stepResult.getUpdatedContext());
					context = stepResult.getUpdatedContext();
				}

				PipelineStatus status = PipelineResult.fromStepResult(stepResult).getStatus();
				if (status == PipelineStatus.CONTINUE)
					continue;
				if (status == PipelineStatus.COMPLETE) {
					completedStage = true;
					break;
				}

				if (status == PipelineStatus.WAITING && requiresForbiddenInteraction(stepResult, pipelineType, journeyMode)) {
					PipelineResult denial = PipelineResult.denied(journeyInteractionRequiredMessage());
					interactionDenials.add(denial);
					return denial;
				}

				if (status == PipelineStatus.WAITING || status == PipelineStatus.REQUIRE_RECONNECT)
					persistPending(pipelineState, context, journeyMode, stage.getId(), index);

				return PipelineResult.fromStepResult(stepResult);
			}

			if (stage.isRequireCompletion() && !completedStage)
				return PipelineResult.failed(journeyNoCompletionMessage());
		}
		return null;
	}

	private @NotNull StepResult executeStep(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode,
			@NotNull JourneyStage stage,
			@NotNull JourneyStep journeyStep,
			@Nullable InternalProvider provider
	) {
		if (requiresOnlinePresence(journeyStep)
				&& !isOnline(context)) {
			Logger.debug(
					"Journey step waiting for online presence pipeline=%s stage=%s step=%s username=%s connection=%s",
					pipelineType,
					stage.getType().id(),
					journeyStep.getStep().getName(),
					context.getUsername(),
					context.getConnectionUniqueId()
			);
			StepResult result = StepResult.waiting("", StepWaitReason.ONLINE);
			routingCoordinator.accept(RoutingSignal.stepFinished(
					context,
					pipelineType,
					stage.getType(),
					journeyStep.getStep(),
					result
			));
			return result;
		}

		if (!journeyStep.getStep().shouldExecute(context))
			return StepResult.proceed(context);

		eventManager.call(new StepPrepareEvent(
				provider != null ? provider.getProvider() : null,
				journeyStep.getStep(),
				context,
				pipelineType,
				journeyMode,
				stage.getType()
		));
		eventManager.call(new StepStartedEvent(
				provider != null ? provider.getProvider() : null,
				journeyStep.getStep(),
				context
		));

		StepResult result;
		try {
			result = journeyStep.getStep().execute(context, pipelineState).join();
			if (result == null)
				result = StepResult.failed(journeyStepNoStatusMessage());
		} catch (Exception exception) {
			result = StepResult.failed(failureMessage(pipelineType));
		}

		eventManager.call(new StepFinishedEvent(
				provider != null ? provider.getProvider() : null,
				journeyStep.getStep(),
				context,
				pipelineType,
				result,
				stage.getType()
		));
		routingCoordinator.accept(RoutingSignal.stepFinished(
				context,
				pipelineType,
				stage.getType(),
				journeyStep.getStep(),
				result
		));
		return result;
	}

	private int resolveStartIndex(
			@NotNull List<JourneyExecutionBlock> blocks,
			@Nullable JourneyStateItem pending,
			@Nullable String pendingProviderId,
			@NotNull ScenarioContext context
	) {
		if (pending == null || pending.getStageId() == null || pending.getStageId().isBlank())
			return 0;

		String stageId = pending.getStageId();
		for (int index = 0; index < blocks.size(); index++) {
			JourneyExecutionBlock block = blocks.get(index);
			if (block == null) continue;

			String effectiveProviderId = resolveEffectiveProviderId(block, context);
			if (effectiveProviderId != null && !effectiveProviderId.isBlank()) {
				if (!matchesProvider(pendingProviderId, effectiveProviderId))
					continue;
			}

			for (JourneyExecutionStage entry : block.stages()) {
				if (entry == null) continue;
				if (entry.stage().getId().equalsIgnoreCase(stageId))
					return index;
			}
		}
		return 0;
	}

	private @NotNull BlockRegion collectFallbackRegion(
			@NotNull List<JourneyExecutionBlock> blocks,
			int startIndex
	) {
		JourneyExecutionBlock seed = blocks.get(startIndex);
		if (seed == null) return new BlockRegion(List.of(), startIndex);

		String groupId = seed.groupId();
		int endIndex = startIndex;
		List<JourneyExecutionBlock> grouped = new ArrayList<>();
		for (int index = startIndex; index < blocks.size(); index++) {
			JourneyExecutionBlock block = blocks.get(index);
			if (block == null) break;
			if (block.policy() != JourneyExecutionPolicy.FALLBACK)
				break;
			if (!block.groupId().equalsIgnoreCase(groupId))
				break;
			grouped.add(block);
			endIndex = index;
		}
		return new BlockRegion(List.copyOf(grouped), endIndex);
	}

	private void persistPending(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull JourneyMode journeyMode,
			@NotNull String stageId,
			int stepIndex
	) {
		JourneyMode resolvedJourneyMode = journeyMode;
		String resolvedStageId = stageId;
		int resolvedStepIndex = stepIndex;
		boolean clearProvider = false;
		String overrideProviderId = null;

		JourneyOverrideItem override = pipelineState.item(JourneyOverrideItem.class).orElse(null);
		if (override != null) {
			if (override.getJourneyMode() != null)
				resolvedJourneyMode = override.getJourneyMode();
			if (override.getStageId() != null && !override.getStageId().isBlank())
				resolvedStageId = override.getStageId();
			if (override.getStepIndex() >= 0)
				resolvedStepIndex = override.getStepIndex();

			clearProvider = override.isClearProvider();
			overrideProviderId = override.getProviderId();
		}

		if (clearProvider) {
			context.setProvider(null);
		} else if (overrideProviderId != null && !overrideProviderId.isBlank()) {
			applyProviderContext(context, overrideProviderId);
		}

		pipelineState.setScenario(context);
		long ttlMs = scenarioSettings(pipelineState.getPipelineType()).pipelineTtlMillis();
		pipelineState.putItem(new JourneyStateItem(resolvedJourneyMode, resolvedStageId, resolvedStepIndex), ttlMs);
	}

	/**
	 * A strictly seamless journey must not prompt: a waiting step that needs player input is refused instead of
	 * silently turning the journey interactive. A merely preferred seamless mode keeps the prompt.
	 */
	private boolean requiresForbiddenInteraction(
			@NotNull StepResult stepResult,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode
	) {
		if (journeyMode != JourneyMode.SEAMLESS) return false;
		if (stepResult.getWaitReason() != StepWaitReason.INPUT) return false;

		return scenarioSettings(pipelineType).getJourneyPolicy() == JourneyPolicy.STRICT;
	}

	private boolean hasProviderBlock(@NotNull JourneyExecutionPlan plan) {
		for (JourneyExecutionBlock block : plan.blocks())
			if (block != null && block.policy() == JourneyExecutionPolicy.FALLBACK && normalizeProviderId(block.providerId()) != null)
				return true;

		return false;
	}

	private boolean isOnline(@NotNull ScenarioContext context) {
		UUID connectionId = context.getConnectionUniqueId();
        return connectionId != null
				&& identityService.findByConnectionUniqueId(connectionId).isPresent();
    }

	private boolean requiresOnlinePresence(@NotNull JourneyStep step) {
		return step.getStep().contextRequirement() == StepContextRequirement.ONLINE;
	}

	private @NotNull Engine.Scenario scenarioSettings(@Nullable PipelineType pipelineType) {
		Engine.Scenarios scenarios = engineProvider.get().getScenarios();
		if (pipelineType == PipelineType.REGISTRATION) return scenarios.getRegistration();
		if (pipelineType == PipelineType.MIGRATION) return scenarios.getMigration();

		return scenarios.getAuthentication();
	}

	private void applyProviderContext(@NotNull ScenarioContext context, @NotNull String providerId) {
		ProviderContext provider = context.getProvider();
		String username = context.getUsername() != null ? context.getUsername() : "";
		if (provider == null) {
			context.setProvider(ProviderContext.builder()
					.providerId(providerId)
					.providerUsername(username)
					.source(ProviderOrigin.AUTO)
					.build());
			provider = context.getProvider();
		} else {
			provider.setProviderId(providerId);
			if (provider.getProviderUsername().isBlank())
				provider.setProviderUsername(username);
			if (provider.getSource() == null)
				provider.setSource(ProviderOrigin.AUTO);
		}

		enrichProviderSubject(context, providerId, provider);
	}

	private void enrichProviderSubject(
			@NotNull ScenarioContext context,
			@NotNull String providerId,
			@NotNull ProviderContext provider
	) {
		String currentSubject = provider.getProviderSubject();
		if (currentSubject != null && !currentSubject.isBlank())
			return;

		if (context.getIdentity() == null)
			return;

		SubjectResolution resolution = providerOperations.resolveSelectedSubject(
				providerId,
				SubjectResolveContext.builder()
						.identity(context.getIdentity())
						.build()
		);
		if (resolution == null)
			return;

		String resolvedSubject = resolution.getProviderSubject();
		if (resolvedSubject == null || resolvedSubject.isBlank())
			return;

		provider.setProviderSubject(resolvedSubject);
	}

	private boolean matchesProvider(@Nullable String expected, @Nullable String actual) {
		if (expected == null || expected.isBlank())
			return actual == null || actual.isBlank();

		if (actual == null || actual.isBlank()) return false;
		return expected.equalsIgnoreCase(actual);
	}

	private @Nullable String resolveEffectiveProviderId(
			@NotNull JourneyExecutionBlock block,
			@NotNull ScenarioContext context
	) {
		String providerId = block.providerId();
		if (providerId != null && !providerId.isBlank())
			return providerId;

		ProviderContext provider = context.getProvider();
		if (provider == null) return null;

		String contextProviderId = provider.getProviderId();
		return contextProviderId != null && !contextProviderId.isBlank()
				? contextProviderId
				: null;
	}

	private @Nullable String resolvePendingProviderId(
			@NotNull ScenarioContext context,
			@Nullable JourneyStateItem pending
	) {
		if (pending == null) return null;

		ProviderContext provider = context.getProvider();
		if (provider == null) return null;

		String providerId = provider.getProviderId();
		if (providerId == null || providerId.isBlank()) return null;

		return providerId;
	}

	private @NotNull ScenarioContext currentScenario(
			@NotNull PipelineState pipelineState,
			@NotNull PipelineType pipelineType,
			@NotNull ScenarioContext fallback
	) {
		ScenarioContext current = pipelineState.getScenario(pipelineType);
		return current != null ? current : fallback;
	}

	private @NotNull String journeyNoCompletionMessage() {
		return String.join("\n", messagesProvider.get()
				.getEngine()
				.getJourney()
				.getStage()
				.getNoCompletion());
	}

	private @NotNull String journeyInteractionRequiredMessage() {
		return String.join("\n", messagesProvider.get()
				.getEngine()
				.getJourney()
				.getStep()
				.getInteractionRequired());
	}

	private @NotNull String journeyStepNoStatusMessage() {
		return String.join("\n", messagesProvider.get()
				.getEngine()
				.getJourney()
				.getStep()
				.getNoStatus());
	}

	private @NotNull String journeyMissingContextMessage(@NotNull PipelineState pipelineState) {
		Messages.Scenarios.Scenario.Errors errors = resolveScenarioErrors(pipelineState);
		return String.join("\n", errors.getJourney().getMissingContext());
	}

	private @NotNull String journeyMissingPlanMessage(@NotNull PipelineState pipelineState) {
		Messages.Scenarios.Scenario.Errors errors = resolveScenarioErrors(pipelineState);
		return String.join("\n", errors.getJourney().getMissingPlan());
	}

	private @NotNull Messages.Scenarios.Scenario.Errors resolveScenarioErrors(@NotNull PipelineState pipelineState) {
		PipelineType pipelineType = pipelineState.getPipelineType();
		Messages.Scenarios scenarios = messagesProvider.get().getScenarios();
		if (pipelineType == PipelineType.REGISTRATION) return scenarios.getRegistration().getErrors();
		if (pipelineType == PipelineType.MIGRATION) return scenarios.getMigration().getErrors();

		return scenarios.getAuthentication().getErrors();
	}

	private @NotNull String failureMessage(@Nullable PipelineType pipelineType) {
		Messages.Scenarios scenarios = messagesProvider.get().getScenarios();
		if (pipelineType == PipelineType.REGISTRATION) return String.join("\n", scenarios.getRegistration().getRegistrationFailed());
		if (pipelineType == PipelineType.MIGRATION) return String.join("\n", scenarios.getMigration().getMigrationFailed());

		return String.join("\n", scenarios.getAuthentication().getAuthenticationFailed());
	}

	private @Nullable InternalProvider resolveProvider(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return null;

		List<InternalProvider> providers = providerManager.getProviders();
		if (providers == null) return null;

		for (InternalProvider provider : providers) {
			if (provider == null || provider.getDescriptor() == null)
				continue;

			String id = provider.getDescriptor().getId();
			if (id.equalsIgnoreCase(providerId))
				return provider;
		}
		return null;
	}

	private record BlockRegion(@NotNull List<JourneyExecutionBlock> blocks, int endIndex) {
	}
}
