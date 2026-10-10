package me.whereareiam.identica.provider.credential.pipeline.step.type.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialConfirmStep;
import org.jetbrains.annotations.NotNull;

@Singleton
public class CredentialMigrationConfirmStep extends AbstractCredentialConfirmStep {
	@Inject
	public CredentialMigrationConfirmStep(
			Provider<CredentialMessages> messagesProvider,
			Provider<Engine> coreSettingsProvider,
			CredentialAccountService credentialService,
			CryptographyService cryptographyService
	) {
		super(
				"password-migration-confirm",
				messagesProvider,
				coreSettingsProvider,
				credentialService,
				cryptographyService
		);
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	protected @NotNull String registerPrompt(@NotNull CredentialMessages messages) {
		return joinLines(messages.getScenario().getMigration().getSetup().getPrompt());
	}

	@Override
	protected @NotNull String confirmPrompt(@NotNull CredentialMessages messages) {
		return joinLines(messages.getScenario().getMigration().getSetup().getConfirmPrompt());
	}

	@Override
	protected @NotNull String mismatchMessage(@NotNull CredentialMessages messages) {
		return messages.getScenario().getMigration().getSetup().getStatus().getMismatch();
	}
}
