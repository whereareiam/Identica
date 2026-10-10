package me.whereareiam.identica.provider.credential.pipeline.step.type.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialPasswordStep;
import org.jetbrains.annotations.NotNull;

@Singleton
public class CredentialMigrationAuthenticationStep extends AbstractCredentialPasswordStep {
	@Inject
	public CredentialMigrationAuthenticationStep(
			Provider<CredentialMessages> messagesProvider,
			Provider<Engine> coreSettingsProvider,
			CredentialAccountService credentialService,
			CryptographyService cryptographyService,
			EventManager eventManager
	) {
		super(
				"password-migration-authentication",
				messagesProvider,
				coreSettingsProvider,
				credentialService,
				cryptographyService,
				eventManager
		);
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	protected @NotNull StepResult handleMissingCredential(
			@NotNull ScenarioContext context,
			@NotNull CredentialMessages messages
	) {
		return StepResult.proceed(context);
	}

	@Override
	protected @NotNull String promptMessage(@NotNull CredentialMessages messages) {
		return joinLines(messages.getScenario().getMigration().getVerification().getPrompt());
	}

	@Override
	protected @NotNull String invalidMessage(@NotNull CredentialMessages messages) {
		return messages.getScenario().getMigration().getVerification().getStatus().getInvalid();
	}

	@Override
	protected @NotNull StepResult successResult(@NotNull ScenarioContext context) {
		return StepResult.complete(context);
	}
}
