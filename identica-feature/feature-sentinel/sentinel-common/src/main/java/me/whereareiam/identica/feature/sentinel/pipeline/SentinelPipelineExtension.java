package me.whereareiam.identica.feature.sentinel.pipeline;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * Adds the sentinel's check to the registration of every provider.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SentinelPipelineExtension implements PipelineExtension {
	private final RegistrationSentinelStep registrationStep;

	@Override
	public @NotNull String id() {
		return "sentinel:steps";
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		builder.registerStep(JourneyStep.builder()
				.stageId(StageType.PROVIDER.id())
				.step(registrationStep)
				.order(registrationStep.order())
				.scenarios(EnumSet.of(PipelineType.REGISTRATION))
				.build());
	}
}
