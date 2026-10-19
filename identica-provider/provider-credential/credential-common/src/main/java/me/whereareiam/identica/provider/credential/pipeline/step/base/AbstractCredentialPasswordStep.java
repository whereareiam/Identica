package me.whereareiam.identica.provider.credential.pipeline.step.base;

import com.google.inject.Provider;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.event.authentication.AuthenticationAttemptDecision;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.pipeline.CredentialAnswers;
import me.whereareiam.identica.provider.credential.pipeline.CredentialPrompts;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractCredentialPasswordStep extends AbstractCredentialAttemptStep {
	protected final Provider<CredentialMessages> messagesProvider;
	private final CredentialAccountService credentialService;
	private final CryptographyService cryptographyService;

	protected AbstractCredentialPasswordStep(
			@NotNull String name,
			@NotNull Provider<CredentialMessages> messagesProvider,
			@NotNull Provider<Engine> coreSettingsProvider,
			@NotNull CredentialAccountService credentialService,
			@NotNull CryptographyService cryptographyService,
			@NotNull EventManager eventManager
	) {
		super(name, coreSettingsProvider, eventManager);
		this.messagesProvider = messagesProvider;
		this.credentialService = credentialService;
		this.cryptographyService = cryptographyService;
	}

	@Override
	public final @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		String providerSubject = requireProviderSubject(context);

		CredentialMessages messages = messagesProvider.get();
		CredentialAccount credential = credentialService.find(providerSubject).orElse(null);
		if (credential == null) return CompletableFuture.completedFuture(handleMissingCredential(context, messages));

		var input = consumeAuthenticationAttempt(state);
		if (input == null) return CompletableFuture.completedFuture(StepResult.waiting(promptMessage(messages))
				.withPrompt(CredentialPrompts.PASSWORD));

		if (!cryptographyService.verify(credential, input.getPassword())) {
			AuthenticationAttemptDecision decision = recordBruteForceDecision(credential, context);
			if (decision.isDeny()) return CompletableFuture.completedFuture(StepResult.denied(decision.getDenyMessage()));
			return CompletableFuture.completedFuture(invalidWithWarning(messages, decision.getWarningMessage()));
		}

		clearBruteForce(credential, context);
		return CompletableFuture.completedFuture(successResult(context));
	}

	protected abstract @NotNull StepResult handleMissingCredential(
			@NotNull ScenarioContext context,
			@NotNull CredentialMessages messages
	);

	protected abstract @NotNull String promptMessage(@NotNull CredentialMessages messages);

	protected abstract @NotNull String invalidMessage(@NotNull CredentialMessages messages);

	protected abstract @NotNull StepResult successResult(@NotNull ScenarioContext context);

	private @NotNull StepResult invalidWithWarning(
			@NotNull CredentialMessages messages,
			String warning
	) {
		String invalid = invalidMessage(messages);
		return StepResult.waiting(joinNonBlank(invalid, warning))
				.withPrompt(CredentialPrompts.PASSWORD)
				.withAnswer(CredentialAnswers.PASSWORD_INVALID);
	}
}
