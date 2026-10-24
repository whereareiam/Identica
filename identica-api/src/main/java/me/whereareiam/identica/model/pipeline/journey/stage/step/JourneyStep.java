package me.whereareiam.identica.model.pipeline.journey.stage.step;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

@Getter
@ToString
@Builder(toBuilder = true)
public class JourneyStep {
	private final @NotNull String stageId;
	private final @NotNull Step step;
	private final int order;
	private final @NotNull Set<PipelineType> scenarios;
	private final @Nullable String providerId;
	/**
	 * Name of the provider method the step belongs to, or {@code null} for a step every method of its provider runs.
	 */
	private final @Nullable String method;

	/**
	 * Tells whether the step runs in a scenario for a provider method.
	 *
	 * @param pipelineType scenario
	 * @param targetProviderId provider that runs, or {@code null} for the steps of no provider
	 * @param targetMethod name of the method that runs, or {@code null} when the provider runs without one
	 * @return {@code true} when the step runs
	 */
	public boolean supports(
			@NotNull PipelineType pipelineType,
			@Nullable String targetProviderId,
			@Nullable String targetMethod
	) {
		Set<PipelineType> supportedScenarios = scenarios.isEmpty()
				? EnumSet.allOf(PipelineType.class)
				: scenarios;
		if (!supportedScenarios.contains(pipelineType)) return false;
		if (providerId == null || providerId.isBlank()) return true;
		if (!providerId.equalsIgnoreCase(targetProviderId)) return false;

		return method == null || method.isBlank() || method.equalsIgnoreCase(targetMethod);
	}

	public int resolvedOrder() {
		if (order != 0) return order;
		return step.order();
	}

	public boolean isProviderSpecific() {
		return providerId != null && !providerId.isBlank();
	}

	public boolean isMethodSpecific() {
		return method != null && !method.isBlank();
	}
}
