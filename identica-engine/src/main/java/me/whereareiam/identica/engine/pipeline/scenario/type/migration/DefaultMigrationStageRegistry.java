package me.whereareiam.identica.engine.pipeline.scenario.type.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.scenario.AbstractJourneyRegistry;
import me.whereareiam.identica.engine.step.EnrollmentStep;
import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;

import java.util.EnumSet;

@Singleton
public class DefaultMigrationStageRegistry extends AbstractJourneyRegistry implements MigrationJourneyRegistry {
	@Inject
	public DefaultMigrationStageRegistry(EnrollmentStep enrollmentStep) {
		PipelineType pipelineType = PipelineType.MIGRATION;
		registerStep(JourneyStep.builder()
				.stageId(StageType.PRE.id())
				.step(enrollmentStep)
				.order(enrollmentStep.order())
				.scenarios(EnumSet.of(pipelineType))
				.build());

		registerStage(JourneyStage.builder()
				.id(StageType.PRE.id())
				.type(StageType.PRE)
				.order(100)
				.pipelineTypes(EnumSet.of(pipelineType))
				.allowFallback(true)
				.build());

		registerStage(JourneyStage.builder()
				.id(StageType.PROVIDER.id())
				.type(StageType.PROVIDER)
				.order(200)
				.pipelineTypes(EnumSet.of(pipelineType))
				.requireCompletion(true)
				.allowFallback(true)
				.build());

		registerStage(JourneyStage.builder()
				.id(StageType.END.id())
				.type(StageType.END)
				.order(300)
				.pipelineTypes(EnumSet.of(pipelineType))
				.allowFallback(true)
				.build());
	}
}
