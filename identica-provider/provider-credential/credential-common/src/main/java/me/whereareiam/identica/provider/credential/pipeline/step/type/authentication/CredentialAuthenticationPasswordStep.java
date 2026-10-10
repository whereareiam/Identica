package me.whereareiam.identica.provider.credential.pipeline.step.type.authentication;

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
		return StepResult.denied(messages.getScenario().getAuthentication().getStatus().getNotRegistered());
	}

	@Override
	protected @NotNull String promptMessage(@NotNull CredentialMessages messages) {
		return joinLines(messages.getScenario().getAuthentication().getPrompt());
	}

	@Override
	protected @NotNull String invalidMessage(@NotNull CredentialMessages messages) {
		return messages.getScenario().getAuthentication().getStatus().getInvalid();
	}

	@Override
	protected @NotNull StepResult successResult(@NotNull ScenarioContext context) {
		return StepResult.proceed(context);
	}
}
