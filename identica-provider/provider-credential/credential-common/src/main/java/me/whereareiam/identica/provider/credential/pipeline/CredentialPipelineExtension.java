package me.whereareiam.identica.provider.credential.pipeline;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.AllArgsConstructor;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.pipeline.step.shared.CredentialValidationStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.authentication.CredentialAuthenticationPasswordStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.authentication.CredentialAuthenticationVerificationStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.authentication.CredentialRecognitionStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.migration.CredentialMigrationAuthenticationStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.migration.CredentialMigrationConfirmStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.migration.CredentialMigrationRegistrationStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.registration.CredentialAccountPresenceStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.registration.CredentialRegistrationConfirmStep;
import me.whereareiam.identica.provider.credential.pipeline.step.type.registration.CredentialRegistrationStep;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor(onConstructor_ = @Inject)
public class CredentialPipelineExtension implements PipelineExtension {
	private final @NotNull Provider<CredentialSettings> settingsProvider;

	// Steps
	private final @NotNull CredentialValidationStep validationStep;

	// Steps - Registration
	private final @NotNull CredentialAccountPresenceStep credentialAccountPresenceStep;
	private final @NotNull CredentialRegistrationStep credentialRegistrationStep;
	private final @NotNull CredentialRegistrationConfirmStep credentialRegistrationConfirmStep;

	// Steps - Authentication
	private final @NotNull CredentialRecognitionStep recognitionStep;
	private final @NotNull CredentialAuthenticationPasswordStep authenticationPasswordStep;
	private final @NotNull CredentialAuthenticationVerificationStep authenticationVerificationStep;

	// Steps - Migration
	private final @NotNull CredentialMigrationAuthenticationStep migrationAuthenticationStep;
	private final @NotNull CredentialMigrationRegistrationStep migrationRegistrationStep;
	private final @NotNull CredentialMigrationConfirmStep migrationConfirmStep;

	public static @NotNull String extensionId() {
		return CredentialConstants.PROVIDER_ID + ":password-auth";
	}

	@Override
	public @NotNull String id() {
		return extensionId();
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		String providerId = CredentialConstants.PROVIDER_ID;
		CredentialSettings settings = settingsProvider.get();
		boolean requireRepeat = settings != null
				&& settings.getScenario() != null
				&& settings.getScenario().getRegistration() != null
				&& settings.getScenario().getRegistration().isRequireRepeat();

		builder.registerStep(
				PipelineType.REGISTRATION,
				providerId,
				StageType.PROVIDER,
				validationStep
		);
		builder.registerStep(
				PipelineType.REGISTRATION,
				providerId,
				StageType.PROVIDER,
				credentialAccountPresenceStep
		);
		builder.registerStep(
				PipelineType.REGISTRATION,
				providerId,
				StageType.PROVIDER,
				credentialRegistrationStep
		);
		if (requireRepeat) {
			builder.registerStep(
					PipelineType.REGISTRATION,
					providerId,
					StageType.PROVIDER,
					credentialRegistrationConfirmStep
			);
		}

		builder.registerStep(
				PipelineType.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				validationStep
		);
		builder.registerStep(
				PipelineType.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				recognitionStep
		);

		builder.registerStep(
				PipelineType.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				authenticationPasswordStep
		);
		builder.registerStep(
				PipelineType.AUTHENTICATION,
				providerId,
				StageType.PROVIDER,
				authenticationVerificationStep
		);

		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				validationStep
		);
		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				migrationAuthenticationStep
		);
		builder.registerStep(
				PipelineType.MIGRATION,
				providerId,
				StageType.PROVIDER,
				migrationRegistrationStep
		);
		if (requireRepeat) {
			builder.registerStep(
					PipelineType.MIGRATION,
					providerId,
					StageType.PROVIDER,
					migrationConfirmStep
			);
		}
	}
}
