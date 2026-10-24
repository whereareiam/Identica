package me.whereareiam.identica.pipeline.extension;

import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Builder used by pipeline extensions to register groups, phases, stages, and steps.
 */
@SuppressWarnings("unused")
public interface PipelineExtensionBuilder {
	/**
	 * Registers a prepare pipeline group.
	 *
	 * @param group prepare pipeline group to register
	 */
	void registerPrepareGroup(@NotNull PipelineGroup<?> group);

	/**
	 * Registers a phase in the prepare pipeline.
	 *
	 * @param groupId target prepare group identifier
	 * @param phase prepare phase to register
	 * @param placement placement relative to existing phases
	 */
	void registerPreparePhase(
			@NotNull String groupId,
			@NotNull PipelinePhase<?> phase,
			@NotNull PhasePlacement placement
	);

	/**
	 * Registers a scenario pipeline group.
	 *
	 * @param pipelineType scenario pipeline to register into
	 * @param group scenario pipeline group to register
	 */
	void registerScenarioGroup(@NotNull PipelineType pipelineType, @NotNull PipelineGroup<?> group);

	/**
	 * Registers a phase in a scenario pipeline.
	 *
	 * @param pipelineType scenario pipeline to register into
	 * @param groupId target scenario group identifier
	 * @param phase scenario phase to register
	 * @param placement placement relative to existing phases
	 */
	void registerScenarioPhase(
			@NotNull PipelineType pipelineType,
			@NotNull String groupId,
			@NotNull PipelinePhase<?> phase,
			@NotNull PhasePlacement placement
	);

	/**
	 * Registers a stage for a scenario pipeline journey.
	 *
	 * @param pipelineType scenario pipeline to register into
	 * @param stage journey stage to register
	 */
	void registerStage(@NotNull PipelineType pipelineType, @NotNull JourneyStage stage);

	/**
	 * Registers a provider method: one way the provider offers to get a scenario done. Its steps run in the provider
	 * stage of the method's scenario, together with the steps the provider registers without a method.
	 *
	 * @param method method to register
	 */
	void registerMethod(@NotNull ProviderMethod method);

	/**
	 * Registers a journey step using the scenarios declared on the step itself.
	 *
	 * @param step journey step to register
	 */
	void registerStep(@NotNull JourneyStep step);

	/**
	 * Registers a journey step for a scenario pipeline. A step of a provider runs in each of its methods.
	 *
	 * @param pipelineType scenario pipeline to register into
	 * @param providerId provider identifier, if the step is provider-specific
	 * @param stageType target journey stage type
	 * @param step step to register
	 */
	default void registerStep(
			@NotNull PipelineType pipelineType,
			@Nullable String providerId,
			@NotNull StageType stageType,
			@NotNull Step step
	) {
		registerStep(JourneyStep.builder()
				.stageId(stageType.id())
				.providerId(providerId)
				.order(step.order())
				.scenarios(EnumSet.of(pipelineType))
				.step(step)
				.build());
	}

	/**
	 * Registers a journey step for the authentication and registration pipelines.
	 *
	 * @param providerId provider identifier, if the step is provider-specific
	 * @param stageType target journey stage type
	 * @param step step to register
	 */
	default void registerStep(
			@Nullable String providerId,
			@NotNull StageType stageType,
			@NotNull Step step
	) {
		registerStep(PipelineType.AUTHENTICATION, providerId, stageType, step);
		registerStep(PipelineType.REGISTRATION, providerId, stageType, step);
	}

}
