package me.whereareiam.identica.provider.credential.pipeline.step.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialPasswordStep;
import org.jetbrains.annotations.NotNull;

@Singleton
public class CredentialAuthenticationPasswordStep extends AbstractCredentialPasswordStep {
	@Inject
	public CredentialAuthenticationPasswordStep(
			Provider<CredentialMessages> messagesProvider,
			Provider<Engine> coreSettingsProvider,
			CredentialAccountService credentialService,
			CryptographyService cryptographyService,
			EventManager eventManager
	) {
		super(
				"password-authentication-password",
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
		return StepResult.denied(CredentialMessageDefinitions.NOT_REGISTERED.create(messages));
	}

	@Override
	protected @NotNull PlayerMessage promptMessage(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.PASSWORD.create(messages);
	}

	@Override
	protected @NotNull PlayerMessage invalidMessage(@NotNull CredentialMessages messages) {
		return CredentialMessageDefinitions.PASSWORD_INVALID.create(messages);
	}

	@Override
	protected @NotNull StepResult successResult(@NotNull ScenarioContext context) {
		return StepResult.proceed(context);
	}
}
