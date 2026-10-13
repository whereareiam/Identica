package me.whereareiam.identica.engine.pipeline.scenario.type.registration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.scenario.AbstractJourneyRegistry;
import me.whereareiam.identica.engine.step.EnrollmentStep;
import me.whereareiam.identica.engine.step.UsernameAvailabilityStep;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;

import java.util.EnumSet;

@Singleton
public class DefaultRegistrationStageRegistry extends AbstractJourneyRegistry implements RegistrationJourneyRegistry {
	@Inject
	public DefaultRegistrationStageRegistry(EnrollmentStep enrollmentStep, UsernameAvailabilityStep usernameAvailabilityStep) {
		registerStep(JourneyStep.builder()
				.stageId(StageType.PRE.id())
				.step(enrollmentStep)
				.order(enrollmentStep.order())
				.scenarios(EnumSet.of(PipelineType.REGISTRATION))
				.journeyModes(EnumSet.of(JourneyMode.INTERACTIVE))
				.build());

		// Part of every provider's registration, ahead of its own steps.
		registerStep(JourneyStep.builder()
				.stageId(StageType.PROVIDER.id())
				.step(usernameAvailabilityStep)
				.order(usernameAvailabilityStep.order())
				.scenarios(EnumSet.of(PipelineType.REGISTRATION))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.build());

		PipelineType pipelineType = PipelineType.REGISTRATION;
		registerStage(JourneyStage.builder()
				.id(StageType.PRE.id())
				.type(StageType.PRE)
				.order(100)
				.pipelineTypes(EnumSet.of(pipelineType))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.allowFallback(true)
				.build());

		registerStage(JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(pipelineType))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.requireCompletion(true)
				.allowFallback(true)
				.build());

		registerStage(JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(pipelineType))
				.journeyModes(EnumSet.allOf(JourneyMode.class))
				.allowFallback(true)
				.build());
	}
}
