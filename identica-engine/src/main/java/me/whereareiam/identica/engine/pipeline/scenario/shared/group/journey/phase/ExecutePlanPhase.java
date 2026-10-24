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
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExecutePlanPhase implements PipelinePhase<JourneyState> {
	private final EventManager eventManager;
	private final Provider<Engine> engineProvider;
	private final Provider<Messages> messagesProvider;
	private final ProviderManager providerManager;
	private final ProviderOperations providerOperations;
	private final RoutingCoordinator routingCoordinator;
	private final ProviderSelector providerSelector;

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
		JourneyExecutionPlan plan = state.getExecutionPlan();
		if (context == null || pipelineType == null) {
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

		PipelineResult result = executePlan(pipelineState, context, pipelineType, pending, plan);
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
			@Nullable JourneyStateItem pending,
			@NotNull JourneyExecutionPlan plan
	) {
		Exclusions exclusions = loadExclusions(pipelineState);

		restart:
		while (true) {
			List<JourneyExecutionBlock> blocks = plan.blocks();
			PendingOption pendingOption = resolvePendingOption(context, pending);
			int startIndex = resolveStartIndex(blocks, pending, pendingOption, context);

			for (int index = startIndex; index < blocks.size(); index++) {
				JourneyExecutionBlock block = blocks.get(index);
				if (block == null) continue;

				if (block.policy() == JourneyExecutionPolicy.FALLBACK) {
					BlockRegion region = collectFallbackRegion(blocks, index);
					FallbackOutcome fallbackResult = executeFallbackBlocks(
							pipelineState,
							context,
							pipelineType,
							pending,
							pendingOption,
							region.blocks()
					);
					if (fallbackResult != null && fallbackResult.result != null) {
						JourneyExecutionBlock failed = fallbackResult.failedBlock;
						if (failed != null && exclusions.exclude(failed)) {
							recordExclusions(pipelineState, pipelineType, exclusions);
							JourneyExecutionPlan updatedPlan = removeExcluded(plan, exclusions);
							if (!hasProvider(updatedPlan, failed.providerId()))
								context.setSelection(providerSelector.exclude(context.getSelection(), failed.providerId()));

							// Another option may still get the scenario done; once none is left, the player learns why
							// the last one could not.
							if (!updatedPlan.equals(plan) && hasOptionBlock(updatedPlan)) {
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
						pending,
						pendingOption,
						block
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
			@Nullable JourneyStateItem pending,
			@Nullable PendingOption pendingOption,
			@NotNull List<JourneyExecutionBlock> blocks
	) {
		boolean anySuccess = false;

		for (JourneyExecutionBlock block : blocks) {
			if (block == null) continue;
			context = currentScenario(pipelineState, pipelineType, context);

			PipelineResult blockResult = executeBlock(
					pipelineState,
					context,
					pipelineType,
					pending,
					pendingOption,
					block
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

			// A provider that refuses the player ends the journey with its own reason; trying the next option would
			// bypass the refusal.
			if (status == PipelineStatus.DENIED)
				return new FallbackOutcome(blockResult, null);

			if (status == PipelineStatus.FAILED || status == PipelineStatus.NO_PENDING) {
				// A migration runs its target provider only: it may try the target's other methods, never another
				// provider.
				if (pipelineType == PipelineType.MIGRATION && block.method() == null)
					return new FallbackOutcome(blockResult, null);

				return new FallbackOutcome(blockResult, block);
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
			@Nullable JourneyStateItem pending,
			@Nullable PendingOption pendingOption,
			@NotNull JourneyExecutionBlock block
	) {
		String effectiveProviderId = resolveEffectiveProviderId(block, context);
		String blockProviderId = block.providerId();
		if (blockProviderId != null && !blockProviderId.isBlank()) {
			applyProviderContext(context, blockProviderId);
			Objects.requireNonNull(context.getProvider()).setMethod(block.method());
		}

		InternalProvider provider = resolveProvider(effectiveProviderId);
		if (blockProviderId != null && provider != null) {
			eventManager.call(new ProviderSelectedEvent(context, provider, block.method()));
		}

		return executeStageEntries(
				pipelineState,
				context,
				pipelineType,
				pending,
				pendingOption,
				block.stages(),
				effectiveProviderId,
				block.method(),
				provider
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

	private @NotNull Exclusions loadExclusions(@NotNull PipelineState pipelineState) {
		JourneyOverrideItem override = pipelineState.get(JourneyOverrideItem.KEY).orElse(null);
		Exclusions exclusions = new Exclusions();
		if (override == null) return exclusions;

		normalizeAll(override.getExcludedProviders(), exclusions.providers);
		normalizeAll(override.getExcludedMethods(), exclusions.methods);
		return exclusions;
	}

	private void normalizeAll(@Nullable List<String> values, @NotNull Set<String> target) {
		if (values == null) return;

		for (String value : values) {
			String normalized = normalizeProviderId(value);
			if (normalized != null)
				target.add(normalized);
		}
	}

	private void recordExclusions(
			@NotNull PipelineState pipelineState,
			@NotNull PipelineType pipelineType,
			@NotNull Exclusions exclusions
	) {
		long ttlMs = scenarioSettings(pipelineType).pipelineTtlMillis();
		if (ttlMs <= 0)
			return;

		JourneyOverrideItem current = pipelineState.get(JourneyOverrideItem.KEY).orElse(null);
		Set<String> providers = new HashSet<>(exclusions.providers);
		Set<String> methods = new HashSet<>(exclusions.methods);
		if (current != null) {
			normalizeAll(current.getExcludedProviders(), providers);
			normalizeAll(current.getExcludedMethods(), methods);
		}

		JourneyOverrideItem updated = new JourneyOverrideItem(
				current != null ? current.getStageId() : null,
				current != null ? current.getStepIndex() : -1,
				current != null && current.isClearProvider(),
				current != null ? current.getProviderId() : null,
				List.copyOf(providers),
				List.copyOf(methods)
		);

		pipelineState.put(JourneyOverrideItem.KEY, updated, ttlMs);
	}

	private @NotNull JourneyExecutionPlan removeExcluded(
			@NotNull JourneyExecutionPlan plan,
			@NotNull Exclusions exclusions
	) {
		if (exclusions.isEmpty())
			return plan;

		List<JourneyExecutionBlock> filtered = new ArrayList<>();
		for (JourneyExecutionBlock block : plan.blocks()) {
			if (block == null) continue;
			if (block.policy() == JourneyExecutionPolicy.FALLBACK && exclusions.excludes(block))
				continue;

			filtered.add(block);
		}

		return new JourneyExecutionPlan(filtered);
	}

	private boolean hasOptionBlock(@NotNull JourneyExecutionPlan plan) {
		for (JourneyExecutionBlock block : plan.blocks())
			if (block != null && block.policy() == JourneyExecutionPolicy.FALLBACK && normalizeProviderId(block.providerId()) != null)
				return true;

		return false;
	}

	private boolean hasProvider(@NotNull JourneyExecutionPlan plan, @Nullable String providerId) {
		for (JourneyExecutionBlock block : plan.blocks())
			if (block != null && matchesProvider(block.providerId(), providerId))
				return true;

		return false;
	}

	private @Nullable String normalizeProviderId(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank())
			return null;
		return providerId.trim().toLowerCase(Locale.ROOT);
	}

	private static final class FallbackOutcome {
		private final @Nullable PipelineResult result;
		private final @Nullable JourneyExecutionBlock failedBlock;

		private FallbackOutcome(@Nullable PipelineResult result, @Nullable JourneyExecutionBlock failedBlock) {
			this.result = result;
			this.failedBlock = failedBlock;
		}
	}

	/**
	 * Providers and methods a journey no longer tries: a failed option of a provider with methods excludes that method,
	 * a failed provider without methods excludes the provider.
	 */
	private static final class Exclusions {
		private final Set<String> providers = new HashSet<>();
		private final Set<String> methods = new HashSet<>();

		private boolean exclude(@NotNull JourneyExecutionBlock block) {
			String providerId = block.providerId();
			if (providerId == null || providerId.isBlank()) return false;
			if (block.method() == null || block.method().isBlank())
				return providers.add(providerId.trim().toLowerCase(Locale.ROOT));

			return methods.add(methodId(providerId, block.method()));
		}

		private boolean excludes(@NotNull JourneyExecutionBlock block) {
			String providerId = block.providerId();
			if (providerId == null || providerId.isBlank()) return false;
			if (providers.contains(providerId.trim().toLowerCase(Locale.ROOT))) return true;

			return block.method() != null && methods.contains(methodId(providerId, block.method()));
		}

		private boolean isEmpty() {
			return providers.isEmpty() && methods.isEmpty();
		}

		private static @NotNull String methodId(@NotNull String providerId, @NotNull String method) {
			return (providerId.trim() + ":" + method.trim()).toLowerCase(Locale.ROOT);
		}
	}

	private @Nullable PipelineResult executeStageEntries(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@Nullable JourneyStateItem pending,
			@Nullable PendingOption pendingOption,
			@NotNull List<JourneyExecutionStage> stages,
			@Nullable String providerId,
			@Nullable String method,
			@Nullable InternalProvider provider
	) {
		boolean resumesHere = pending != null
				&& pending.getStageId() != null
				&& !pending.getStageId().isBlank()
				&& (providerId == null || providerId.isBlank() || matchesPendingOption(pendingOption, providerId, method));

		int startStageIndex = 0;
		if (resumesHere) {
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
			if (resumesHere && pending.getStageId().equalsIgnoreCase(stage.getId()))
				startIndex = Math.max(0, pending.getStepIndex());

			boolean completedStage = false;
			List<JourneyStep> steps = entry.steps();
			for (int index = startIndex; index < steps.size(); index++) {
				JourneyStep journeyStep = steps.get(index);
				StepResult stepResult = executeStep(
						pipelineState,
						context,
						pipelineType,
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

				if (status == PipelineStatus.WAITING || status == PipelineStatus.REQUIRE_RECONNECT)
					persistPending(pipelineState, context, stage.getId(), resumeIndex(steps, index, stepResult));

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
			@NotNull JourneyStage stage,
			@NotNull JourneyStep journeyStep,
			@Nullable InternalProvider provider
	) {
		if (!journeyStep.getStep().shouldExecute(context))
			return StepResult.proceed(context);

		eventManager.call(new StepPrepareEvent(
				provider != null ? provider.getProvider() : null,
				journeyStep.getStep(),
				context,
				pipelineType,
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
			if (!(unwrap(exception) instanceof CancellationException))
				Logger.severe(exception, "Step %s failed in %s", journeyStep.getStep().getName(), pipelineType);
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
			@Nullable PendingOption pendingOption,
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
				if (!matchesPendingOption(pendingOption, effectiveProviderId, block.method()))
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

	/**
	 * Returns where a waiting journey continues: at the waiting step, or at the earlier step of the stage its result
	 * names.
	 */
	private static int resumeIndex(@NotNull List<JourneyStep> steps, int waiting, @NotNull StepResult result) {
		String resumeStep = result.getResumeStep();
		if (resumeStep == null) return waiting;

		for (int index = 0; index < waiting; index++)
			if (steps.get(index).getStep().getName().equalsIgnoreCase(resumeStep)) return index;

		Logger.warn("Journey step %s asks to continue at %s, which its stage does not run before it",
				steps.get(waiting).getStep().getName(), resumeStep);
		return waiting;
	}

	private void persistPending(
			@NotNull PipelineState pipelineState,
			@NotNull ScenarioContext context,
			@NotNull String stageId,
			int stepIndex
	) {
		String resolvedStageId = stageId;
		int resolvedStepIndex = stepIndex;
		boolean clearProvider = false;
		String overrideProviderId = null;

		JourneyOverrideItem override = pipelineState.get(JourneyOverrideItem.KEY).orElse(null);
		if (override != null) {
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
		JourneyStateItem position = new JourneyStateItem(resolvedStageId, resolvedStepIndex);
		pipelineState.put(JourneyStateItem.KEY, position, ttlMs);
	}

	private @NotNull Engine.Scenario scenarioSettings(@Nullable PipelineType pipelineType) {
		Engine.Scenarios scenarios = engineProvider.get().getScenarios();
		if (pipelineType == PipelineType.REGISTRATION) return scenarios.getRegistration();
		if (pipelineType == PipelineType.MIGRATION) return scenarios.getMigration();

		return scenarios.getAuthentication();
	}

	/**
	 * Makes a provider the one that runs. Switching to another provider forgets the method of the previous one.
	 */
	private void applyProviderContext(@NotNull ScenarioContext context, @NotNull String providerId) {
		ProviderContext provider = context.getProvider();
		String username = context.getUsername() != null ? context.getUsername() : "";
		if (provider == null) {
			provider = ProviderContext.of(providerId, null, username);
			context.setProvider(provider);
		} else {
			if (!providerId.equalsIgnoreCase(provider.getProviderId()))
				provider.setMethod(null);
			provider.setProviderId(providerId);
			if (provider.getProviderUsername().isBlank())
				provider.setProviderUsername(username);
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

	/**
	 * The option a waiting journey runs: the provider of the context and its method.
	 */
	private @Nullable PendingOption resolvePendingOption(
			@NotNull ScenarioContext context,
			@Nullable JourneyStateItem pending
	) {
		if (pending == null) return null;

		ProviderContext provider = context.getProvider();
		if (provider == null) return null;

		String providerId = provider.getProviderId();
		if (providerId == null || providerId.isBlank()) return null;

		return new PendingOption(providerId, provider.getMethod());
	}

	/**
	 * Tells whether a block runs the option a waiting journey continues with. A provider the player picked without
	 * naming a method continues with its first block, the method the options put first.
	 */
	private boolean matchesPendingOption(
			@Nullable PendingOption pendingOption,
			@Nullable String providerId,
			@Nullable String method
	) {
		if (pendingOption == null) return providerId == null || providerId.isBlank();
		if (!matchesProvider(pendingOption.providerId(), providerId)) return false;
		if (pendingOption.method() == null || pendingOption.method().isBlank()) return true;

		return pendingOption.method().equalsIgnoreCase(method);
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

	private record PendingOption(@NotNull String providerId, @Nullable String method) {
	}

	private static @NotNull Throwable unwrap(@NotNull Throwable failure) {
		return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
	}
}
