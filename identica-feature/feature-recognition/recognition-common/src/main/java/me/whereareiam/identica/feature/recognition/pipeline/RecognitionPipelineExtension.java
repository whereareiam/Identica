package me.whereareiam.identica.feature.recognition.pipeline;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.feature.recognition.pipeline.scenario.type.authentication.group.identity.phase.AuthenticationRecognitionSnapshotPhase;
import me.whereareiam.identica.feature.recognition.pipeline.scenario.type.registration.group.identity.phase.RegistrationRecognitionSnapshotPhase;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RecognitionPipelineExtension implements PipelineExtension {
	private final AuthenticationRecognitionSnapshotPhase authenticationRecognitionSnapshotPhase;
	private final RegistrationRecognitionSnapshotPhase registrationRecognitionSnapshotPhase;

	@Override
	public @NotNull String id() {
		return "recognition:phases";
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		builder.registerScenarioPhase(
				PipelineType.AUTHENTICATION,
				"identity",
				authenticationRecognitionSnapshotPhase,
				PhasePlacement.after("refresh-provider-profile")
		);
		builder.registerScenarioPhase(
				PipelineType.REGISTRATION,
				"identity",
				registrationRecognitionSnapshotPhase,
				PhasePlacement.after("link-provider")
		);
	}
}
