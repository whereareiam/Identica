package me.whereareiam.identica.provider.premium.pipeline;

import me.whereareiam.identica.model.pipeline.journey.stage.JourneyStage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.FinalizeProfileStep;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.OfflineCheckStep;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.ProfilePresenceStep;
import me.whereareiam.identica.provider.premium.pipeline.step.type.authentication.PremiumRecognitionStep;
import me.whereareiam.identica.provider.premium.pipeline.step.type.authentication.PremiumVerificationStep;
import me.whereareiam.identica.provider.premium.pipeline.step.type.migration.PremiumMigrationCompleteStep;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@DisplayName("PremiumPipelineExtension")
class PremiumPipelineExtensionTest {
	private static final String PROVIDER = "premium";

	@Mock
	private ProfilePresenceStep profilePresenceStep;
	@Mock
	private OfflineCheckStep offlineCheckStep;
	@Mock
	private FinalizeProfileStep finalizeProfileStep;
	@Mock
	private PremiumMigrationCompleteStep migrationCompleteStep;
	@Mock
	private PremiumRecognitionStep recognitionStep;
	@Mock
	private PremiumVerificationStep verificationStep;

	@Test
	@DisplayName("completes authentication in both journey modes")
	void registersTheStepsThatCompleteAuthenticationForBothJourneyModes() {
		List<JourneyStep> registered = registered();

		for (Step completing : List.of(recognitionStep, verificationStep))
			for (JourneyMode mode : JourneyMode.values())
				assertTrue(registered.stream().anyMatch(step -> step.getStep() == completing
								&& step.getScenarios().contains(PipelineType.AUTHENTICATION)
								&& PROVIDER.equals(step.getProviderId())
								&& step.getJourneyModes().contains(mode)),
						completing + " must take part in " + mode + " authentication");
	}

	private List<JourneyStep> registered() {
		RecordingBuilder builder = new RecordingBuilder();
		new PremiumPipelineExtension(PROVIDER, profilePresenceStep, offlineCheckStep, finalizeProfileStep,
				migrationCompleteStep, recognitionStep, verificationStep).apply(builder);

		return builder.steps;
	}

	private static final class RecordingBuilder implements PipelineExtensionBuilder {
		private final List<JourneyStep> steps = new ArrayList<>();

		@Override
		public void registerStep(@NotNull JourneyStep step) {
			steps.add(step);
		}

		/**
		 * Registers a step without explicit journey modes, as the engine's builder does.
		 */
		@Override
		public void registerStep(
				@NotNull PipelineType pipelineType,
				@Nullable String providerId,
				@NotNull StageType stageType,
				@NotNull Step step
		) {
			steps.add(JourneyStep.builder()
					.stageId(stageType.id())
					.providerId(providerId)
					.scenarios(EnumSet.of(pipelineType))
					.journeyModes(Set.of())
					.step(step)
					.build());
		}

		@Override
		public void registerPrepareGroup(@NotNull PipelineGroup<?> group) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void registerPreparePhase(@NotNull String groupId, @NotNull PipelinePhase<?> phase, @NotNull PhasePlacement placement) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void registerScenarioGroup(@NotNull PipelineType pipelineType, @NotNull PipelineGroup<?> group) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void registerScenarioPhase(
				@NotNull PipelineType pipelineType,
				@NotNull String groupId,
				@NotNull PipelinePhase<?> phase,
				@NotNull PhasePlacement placement
		) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void registerStage(@NotNull PipelineType pipelineType, @NotNull JourneyStage stage) {
			throw new UnsupportedOperationException();
		}
	}
}
