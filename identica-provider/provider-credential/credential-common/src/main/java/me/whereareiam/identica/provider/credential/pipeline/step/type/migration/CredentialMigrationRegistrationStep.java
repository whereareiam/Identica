package me.whereareiam.identica.provider.credential.pipeline.step.type.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialSetupStep;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class CredentialMigrationRegistrationStep extends AbstractCredentialSetupStep {
	@Inject
	public CredentialMigrationRegistrationStep(
			Provider<CredentialMessages> messagesProvider,
			Provider<CredentialSettings> settingsProvider,
			Provider<Engine> coreSettingsProvider,
			CredentialAccountService credentialService,
			CryptographyService cryptographyService,
			PasswordRules passwordPolicy
	) {
		super(
				"password-migration-registration",
				messagesProvider,
				settingsProvider,
				coreSettingsProvider,
				credentialService,
				cryptographyService,
				passwordPolicy
		);
	}

	@Override
	public int order() {
		return 15;
	}

	@Override
	protected long ttlMs() {
		return migrationTtlMs();
	}

	@Override
	protected @Nullable StepResult existingCredentialResult(
			@NotNull ScenarioContext context,
			@Nullable CredentialAccount credential
	) {
		return credential != null ? StepResult.proceed(context) : null;
	}

	@Override
	protected @NotNull String disabledMessage(@NotNull CredentialMessages messages) {
		return messages.getScenario().getMigration().getSetup().getStatus().getDisabled();
	}

	@Override
	protected @NotNull String registerPrompt(@NotNull CredentialMessages messages) {
		return joinLines(messages.getScenario().getMigration().getSetup().getPrompt());
	}

	@Override
	protected @NotNull String noPendingMessage(@NotNull CredentialMessages messages) {
		return messages.getScenario().getMigration().getSetup().getStatus().getNoPending();
	}
}
