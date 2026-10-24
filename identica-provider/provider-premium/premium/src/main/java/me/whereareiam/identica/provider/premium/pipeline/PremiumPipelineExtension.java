package me.whereareiam.identica.provider.premium.pipeline;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.FinalizeProfileStep;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.OfflineCheckStep;
import me.whereareiam.identica.provider.premium.pipeline.step.shared.ProfilePresenceStep;
import me.whereareiam.identica.provider.premium.pipeline.step.type.authentication.PremiumRecognitionStep;
import me.whereareiam.identica.provider.premium.pipeline.step.type.authentication.PremiumVerificationStep;
import me.whereareiam.identica.provider.premium.pipeline.step.type.migration.PremiumMigrationCompleteStep;
import me.whereareiam.identica.type.pipeline.PipelineType;
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
				PipelineType.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				premiumRecognitionStep
		);
		builder.registerStep(
				PipelineType.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				premiumVerificationStep
		);

		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				profilePresenceStep
		);
		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				offlineCheckStep
		);
		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				finalizeProfileStep
		);
		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				premiumMigrationCompleteStep
		);
	}
}
