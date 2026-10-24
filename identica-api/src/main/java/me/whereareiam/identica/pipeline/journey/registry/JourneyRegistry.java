package me.whereareiam.identica.pipeline.journey.registry;

import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Registry for journey stages, steps and provider methods.
 */
@SuppressWarnings("unused")
public interface JourneyRegistry {
	/**
	 * Registers a journey stage.
	 *
	 * @param stage stage to register
	 */
	void registerStage(@NotNull JourneyStage stage);

	/**
	 * Unregisters a journey stage.
	 *
	 * @param stage stage to unregister
	 */
	void unregisterStage(@NotNull JourneyStage stage);

	/**
	 * Unregisters a journey stage by id.
	 *
	 * @param stageId stage identifier
	 * @return {@code true} if a stage was removed
	 */
	boolean unregisterStage(@NotNull String stageId);

	/**
	 * Registers a journey step.
	 *
	 * @param step step to register
	 */
	void registerStep(@NotNull JourneyStep step);

	/**
	 * Registers a provider method and its steps in the provider stage. A method with the name of one its provider
	 * already registered replaces it.
	 *
	 * @param method method to register
	 */
	void registerMethod(@NotNull ProviderMethod method);

	/**
	 * Removes a provider method and its steps.
	 *
	 * @param providerId provider identifier
	 * @param name method name
	 * @return {@code true} if a method was removed
	 */
	boolean removeMethod(@NotNull String providerId, @NotNull String name);

	/**
	 * Removes a previously registered step by name.
	 *
	 * @param stageId stage identifier
	 * @param providerId provider identifier
	 * @param pipelineType scenario pipeline type
	 * @param stepName step name
	 * @return {@code true} if a step was removed
	 */
	boolean removeStep(
			@NotNull String stageId,
			@Nullable String providerId,
			@NotNull PipelineType pipelineType,
			@NotNull String stepName
	);

	/**
	 * Returns the methods a provider registered, in the order it registered them.
	 *
	 * @param providerId provider identifier
	 * @return the provider's methods, empty when it runs all its steps as one option
	 */
	@NotNull List<ProviderMethod> methods(@NotNull String providerId);

	/**
	 * Resolves a journey plan for the given context, provider and method.
	 *
	 * @param context scenario context
	 * @param pipelineType scenario pipeline type
	 * @param providerId provider identifier, or {@code null} for the steps of no provider
	 * @param method name of the provider's method, or {@code null} for a provider without methods
	 * @return resolved journey plan
	 */
	@NotNull JourneyPlan resolvePlan(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@Nullable String providerId,
			@Nullable String method
	);

	/**
	 * Resolves the journey plan of the steps of no provider.
	 *
	 * @param context scenario context
	 * @param pipelineType scenario pipeline type
	 * @return resolved journey plan
	 */
	default @NotNull JourneyPlan resolvePlan(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType
	) {
		return resolvePlan(context, pipelineType, null, null);
	}

	/**
	 * Returns all registered stages.
	 *
	 * @return registered stages
	 */
	@NotNull List<JourneyStage> getAllStages();

	/**
	 * Convenience registration of a step.
	 *
	 * @param providerId provider identifier
	 * @param stageType stage type
	 * @param order step order override
	 * @param pipelineType scenario pipeline type
	 * @param step step to register
	 */
	default void registerStep(
			@Nullable String providerId,
			@NotNull StageType stageType,
			int order,
			@NotNull PipelineType pipelineType,
			@NotNull Step step
	) {
		registerStep(JourneyStep.builder()
				.stageId(stageType.id())
				.providerId(providerId)
				.order(order)
				.scenarios(EnumSet.of(pipelineType))
				.step(step)
				.build());
	}
}
