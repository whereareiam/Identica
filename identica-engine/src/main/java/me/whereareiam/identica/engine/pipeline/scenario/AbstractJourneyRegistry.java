package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.registry.JourneyRegistry;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractJourneyRegistry implements JourneyRegistry {
	private final List<JourneyStage> stages = new CopyOnWriteArrayList<>();
	private final List<JourneyStep> steps = new CopyOnWriteArrayList<>();
	private final List<ProviderMethod> methods = new CopyOnWriteArrayList<>();

	@Override
	public void registerStage(@NotNull JourneyStage stage) {
		if (stage.getId().isBlank())
			return;

		unregisterStage(stage.getId());
		stages.add(stage);
		Logger.debug("Registered journey stage %s in %s", stage.getId(), registryLabel());
	}

	@Override
	public void unregisterStage(@NotNull JourneyStage stage) {
		unregisterStage(stage.getId());
	}

	@Override
	public boolean unregisterStage(@NotNull String stageId) {
		if (stageId.isBlank())
			return false;
		String normalized = normalize(stageId);
		boolean removed = stages.removeIf(stage -> normalize(stage.getId()).equals(normalized));
		if (removed) {
			steps.removeIf(step -> normalize(step.getStageId()).equals(normalized));
		}
		return removed;
	}

	@Override
	public void registerStep(@NotNull JourneyStep step) {
		if (step.getStageId().isBlank() || step.getStep().getName().isBlank())
			return;

		boolean replaced = steps.removeIf(existing -> isDuplicate(existing, step));
		if (replaced) {
			Logger.warn(
					"Journey step %s already registered for stage %s, replacing registration",
					step.getStep().getName(),
					step.getStageId()
			);
		}

		steps.add(step);
	}

	@Override
	public void registerMethod(@NotNull ProviderMethod method) {
		if (method.getProviderId().isBlank() || method.getName().isBlank())
			return;

		if (removeMethod(method.getProviderId(), method.getName()))
			Logger.warn("Provider method %s already registered in %s, replacing registration", method.id(), registryLabel());

		methods.add(method);
		for (Step step : method.getSteps()) {
			registerStep(JourneyStep.builder()
					.stageId(StageType.PROVIDER.id())
					.providerId(method.getProviderId())
					.method(method.getName())
					.order(step.order())
					.scenarios(EnumSet.of(method.getScenario()))
					.step(step)
					.build());
		}
		Logger.debug("Registered provider method %s in %s", method.id(), registryLabel());
	}

	@Override
	public boolean removeMethod(@NotNull String providerId, @NotNull String name) {
		boolean removed = methods.removeIf(method -> method.getProviderId().equalsIgnoreCase(providerId)
				&& method.getName().equalsIgnoreCase(name));
		if (removed) {
			steps.removeIf(step -> matchesProvider(step.getProviderId(), providerId)
					&& step.isMethodSpecific()
					&& name.equalsIgnoreCase(step.getMethod()));
		}

		return removed;
	}

	@Override
	public boolean removeStep(
			@NotNull String stageId,
			@Nullable String providerId,
			@NotNull PipelineType pipelineType,
			@NotNull String stepName
	) {
		if (stageId.isBlank() || stepName.isBlank())
			return false;

		String normalizedStageId = normalize(stageId);
		return steps.removeIf(step -> {
			if (!normalize(step.getStageId()).equals(normalizedStageId))
				return false;
			if (!step.getStep().getName().equalsIgnoreCase(stepName))
				return false;
			if (!safeScenarios(step.getScenarios()).contains(pipelineType))
				return false;
			return matchesProvider(step.getProviderId(), providerId);
		});
	}

	@Override
	public @NotNull List<ProviderMethod> methods(@NotNull String providerId) {
		return methods.stream()
				.filter(method -> method.getProviderId().equalsIgnoreCase(providerId))
				.toList();
	}

	@Override
	public @NotNull JourneyPlan resolvePlan(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@Nullable String providerId,
			@Nullable String method
	) {
		List<JourneyStage> sortedStages = new ArrayList<>();
		for (JourneyStage stage : stages) {
			if (stage == null)
				continue;
			if (!stage.supports(pipelineType))
				continue;
			sortedStages.add(stage);
		}

		sortedStages.sort(Comparator
				.comparingInt((JourneyStage stage) -> stage.getType().order())
				.thenComparingInt(JourneyStage::getOrder)
				.thenComparing(JourneyStage::getId, String.CASE_INSENSITIVE_ORDER));

		List<JourneyPlan.StageEntry> entries = new ArrayList<>();
		for (JourneyStage stage : sortedStages) {
			List<JourneyStep> resolvedSteps = resolveStageSteps(stage, pipelineType, providerId, method);
			entries.add(new JourneyPlan.StageEntry(stage, resolvedSteps));
		}

		return new JourneyPlan(entries);
	}

	@Override
	public @NotNull List<JourneyStage> getAllStages() {
		List<JourneyStage> snapshot = new ArrayList<>(stages);
		snapshot.sort(Comparator
				.comparingInt((JourneyStage stage) -> stage.getType().order())
				.thenComparingInt(JourneyStage::getOrder)
				.thenComparing(JourneyStage::getId, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(snapshot);
	}

	private @NotNull List<JourneyStep> resolveStageSteps(
			@NotNull JourneyStage stage,
			@NotNull PipelineType pipelineType,
			@Nullable String providerId,
			@Nullable String method
	) {
		Map<String, JourneyStep> selected = new LinkedHashMap<>();
		String stageId = normalize(stage.getId());

		for (JourneyStep candidate : steps) {
			if (candidate == null)
				continue;
			if (!normalize(candidate.getStageId()).equals(stageId))
				continue;
			if (!candidate.supports(pipelineType, providerId, method))
				continue;

			String stepName = candidate.getStep().getName().toLowerCase(Locale.ROOT);
			JourneyStep existing = selected.get(stepName);
			if (existing == null || specificity(candidate) > specificity(existing))
				selected.put(stepName, candidate);
		}

		List<JourneyStep> resolved = new ArrayList<>(selected.values());
		resolved.sort(Comparator
				.comparingInt(JourneyStep::resolvedOrder)
				.thenComparing(step -> step.getStep().getName(), String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(resolved);
	}

	/**
	 * Ranks registrations of one step: a provider's own before one for every provider, a method's own before one for
	 * every method of its provider.
	 */
	private int specificity(@NotNull JourneyStep step) {
		int score = 0;
		if (step.isProviderSpecific())
			score += 2;
		if (step.isMethodSpecific())
			score += 1;

		return score;
	}

	private boolean isDuplicate(@NotNull JourneyStep existing, @NotNull JourneyStep incoming) {
		if (!normalize(existing.getStageId()).equals(normalize(incoming.getStageId())))
			return false;
		if (!existing.getStep().getName().equalsIgnoreCase(incoming.getStep().getName()))
			return false;
		if (!matchesProvider(existing.getProviderId(), incoming.getProviderId()))
			return false;
		if (!matchesMethod(existing.getMethod(), incoming.getMethod()))
			return false;
		return safeScenarios(existing.getScenarios()).equals(safeScenarios(incoming.getScenarios()));
	}

	private boolean matchesProvider(@Nullable String registeredProviderId, @Nullable String providerId) {
		if (registeredProviderId == null || registeredProviderId.isBlank())
			return providerId == null || providerId.isBlank();
		if (providerId == null || providerId.isBlank())
			return false;
		return registeredProviderId.equalsIgnoreCase(providerId);
	}

	private boolean matchesMethod(@Nullable String registeredMethod, @Nullable String method) {
		if (registeredMethod == null || registeredMethod.isBlank())
			return method == null || method.isBlank();
		return registeredMethod.equalsIgnoreCase(method);
	}

	private @NotNull String normalize(@NotNull String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private @NotNull Set<PipelineType> safeScenarios(@Nullable Set<PipelineType> values) {
		if (values == null || values.isEmpty())
			return EnumSet.allOf(PipelineType.class);
		return values;
	}

	protected @NotNull String registryLabel() {
		return getClass().getSimpleName();
	}
}
