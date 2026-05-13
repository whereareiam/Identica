package me.whereareiam.identica.provider.password.pipeline;

import com.google.inject.Provider;
import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.pipeline.scenario.PasswordValidationStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.authentication.PasswordSessionReuseStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.authentication.PasswordAuthenticationPasswordStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.authentication.PasswordAuthenticationVerificationStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.migration.PasswordMigrationAuthenticationStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.migration.PasswordMigrationConfirmStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.migration.PasswordMigrationRegistrationStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.registration.PasswordRegistrationConfirmStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.registration.PasswordAccountPresenceStep;
import me.whereareiam.identica.provider.password.pipeline.scenario.registration.PasswordRegistrationStep;
import me.whereareiam.identica.type.pipeline.PipelineScope;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor(onConstructor_ = @Inject)
public class PasswordPipelineExtension implements PipelineExtension {
	private final @NotNull Provider<PasswordSettings> settingsProvider;

	// Steps
	private final @NotNull PasswordValidationStep validationStep;

	// Steps - Registration
	private final @NotNull PasswordAccountPresenceStep passwordAccountPresenceStep;
	private final @NotNull PasswordRegistrationStep passwordRegistrationStep;
	private final @NotNull PasswordRegistrationConfirmStep passwordRegistrationConfirmStep;

	// Steps - Authentication
	private final @NotNull PasswordSessionReuseStep sessionReuseStep;
	private final @NotNull PasswordAuthenticationPasswordStep authenticationPasswordStep;
	private final @NotNull PasswordAuthenticationVerificationStep authenticationVerificationStep;

	// Steps - Migration
	private final @NotNull PasswordMigrationAuthenticationStep migrationAuthenticationStep;
	private final @NotNull PasswordMigrationRegistrationStep migrationRegistrationStep;
	private final @NotNull PasswordMigrationConfirmStep migrationConfirmStep;

	public static @NotNull String extensionId() {
		return PasswordConstants.PROVIDER_ID + ":password-auth";
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
		String providerId = PasswordConstants.PROVIDER_ID;
		PasswordSettings settings = settingsProvider.get();
		boolean requireRepeat = settings != null
				&& settings.getScenario() != null
				&& settings.getScenario().getRegistration() != null
				&& settings.getScenario().getRegistration().isRequireRepeat();

		registerForBothJourneyModes(
				builder,
				PipelineScope.REGISTRATION,
				providerId,
				PipelineType.REGISTRATION,
				validationStep
		);
		registerForBothJourneyModes(
				builder,
				PipelineScope.REGISTRATION,
				providerId,
				PipelineType.REGISTRATION,
				passwordAccountPresenceStep
		);
		registerForBothJourneyModes(
				builder,
				PipelineScope.REGISTRATION,
				providerId,
				PipelineType.REGISTRATION,
				passwordRegistrationStep
		);
		if (requireRepeat) {
			registerForBothJourneyModes(
					builder,
					PipelineScope.REGISTRATION,
					providerId,
					PipelineType.REGISTRATION,
					passwordRegistrationConfirmStep
			);
		}

		registerForBothJourneyModes(
				builder,
				PipelineScope.AUTHENTICATION,
				providerId,
				PipelineType.AUTHENTICATION,
				validationStep
		);
		registerForBothJourneyModes(
				builder,
				PipelineScope.AUTHENTICATION,
				providerId,
				PipelineType.AUTHENTICATION,
				sessionReuseStep
		);

		registerForBothJourneyModes(
				builder,
				PipelineScope.AUTHENTICATION,
				providerId,
				PipelineType.AUTHENTICATION,
				authenticationPasswordStep
		);
		registerForBothJourneyModes(
				builder,
				PipelineScope.AUTHENTICATION,
				providerId,
				PipelineType.AUTHENTICATION,
				authenticationVerificationStep
		);

		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				validationStep
		);
		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				migrationAuthenticationStep
		);
		builder.registerStep(
				PipelineScope.MIGRATION,
				providerId,
				StageType.PROVIDER,
				PipelineType.MIGRATION,
				migrationRegistrationStep
		);
		if (requireRepeat) {
			builder.registerStep(
					PipelineScope.MIGRATION,
					providerId,
					StageType.PROVIDER,
					PipelineType.MIGRATION,
					migrationConfirmStep
			);
		}
	}

	private void registerForBothJourneyModes(
			@NotNull PipelineExtensionBuilder builder,
			@NotNull PipelineScope scope,
			@NotNull String providerId,
			@NotNull PipelineType pipelineType,
			@NotNull Step step
	) {
		builder.registerStep(scope, providerId, StageType.PROVIDER, pipelineType, JourneyMode.SEAMLESS, step);
		builder.registerStep(scope, providerId, StageType.PROVIDER, pipelineType, JourneyMode.INTERACTIVE, step);
	}
}
