package me.whereareiam.identica.provider.premium.pipeline;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.provider.premium.step.*;
import me.whereareiam.identica.type.pipeline.PipelineScope;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class PremiumPipelineExtension implements PipelineExtension {
	private final @NotNull String providerId;

	// Steps
	private final @NotNull ProfilePresenceStep profilePresenceStep;
	private final @NotNull OfflineCheckStep offlineCheckStep;
	private final @NotNull FinalizeProfileStep finalizeProfileStep;
	private final @NotNull PremiumMigrationCompleteStep premiumMigrationCompleteStep;
	private final @NotNull PremiumRecognitionStep premiumRecognitionStep;
	private final @NotNull PremiumVerificationStep premiumVerificationStep;

	public static @NotNull String extensionIdFor(@NotNull String providerId) {
		return providerId + ":verify-resolver";
	}

	@Override
	public @NotNull String id() {
		return extensionIdFor(providerId);
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		builder.registerStep(
				providerId,
				StageType.PROVIDER,
				profilePresenceStep
		);
		builder.registerStep(
				providerId,
				StageType.PROVIDER,
				offlineCheckStep
		);
		builder.registerStep(
				providerId,
				StageType.PROVIDER,
				finalizeProfileStep
		);
		builder.registerStep(
				PipelineScope.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.AUTHENTICATION,
				JourneyMode.SEAMLESS,
				premiumRecognitionStep
		);
		builder.registerStep(
				PipelineScope.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.AUTHENTICATION,
				JourneyMode.INTERACTIVE,
				premiumRecognitionStep
		);
		builder.registerStep(
				PipelineScope.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.AUTHENTICATION,
				JourneyMode.SEAMLESS,
				premiumVerificationStep
		);
		builder.registerStep(
				PipelineScope.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.AUTHENTICATION,
				JourneyMode.INTERACTIVE,
				premiumVerificationStep
		);

		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				profilePresenceStep
		);
		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				offlineCheckStep
		);
		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				finalizeProfileStep
		);
		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				premiumMigrationCompleteStep
		);
	}
}
