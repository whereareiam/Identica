package me.whereareiam.identica.provider.credential.pipeline.step.type.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
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
	/**
	 * The step's name.
	 */
	public static final String NAME = "password-migration-registration";

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
				NAME,
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
	protected @NotNull PlayerMessage disabledMessage(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.MIGRATION_DISABLED.create(messages);
	}

	@Override
	protected @NotNull PlayerMessage registerPrompt(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.MIGRATION_NEW_PASSWORD.create(messages);
	}

	@Override
	protected @NotNull PlayerMessage noPendingMessage(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.MIGRATION_NO_NEW_PASSWORD.create(messages);
	}
}
