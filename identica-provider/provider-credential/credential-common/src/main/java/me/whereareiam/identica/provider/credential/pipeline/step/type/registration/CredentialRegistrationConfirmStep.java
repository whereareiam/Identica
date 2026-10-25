package me.whereareiam.identica.provider.credential.pipeline.step.type.registration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialConfirmStep;
import org.jetbrains.annotations.NotNull;

@Singleton
public class CredentialRegistrationConfirmStep extends AbstractCredentialConfirmStep {
	@Inject
	public CredentialRegistrationConfirmStep(
			Provider<CredentialMessages> messagesProvider,
			Provider<Engine> coreSettingsProvider,
			CredentialAccountService credentialService,
			CryptographyService cryptographyService
	) {
		super(
				"password-registration-confirm",
				CredentialRegistrationStep.NAME,
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
	protected @NotNull PlayerMessage registerPrompt(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.NEW_PASSWORD.create(messages);
	}

	@Override
	protected @NotNull PlayerMessage confirmPrompt(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.REPEAT_PASSWORD.create(messages);
	}

	@Override
	protected @NotNull PlayerMessage mismatchMessage(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.PASSWORDS_DIFFER.create(messages);
	}
}
