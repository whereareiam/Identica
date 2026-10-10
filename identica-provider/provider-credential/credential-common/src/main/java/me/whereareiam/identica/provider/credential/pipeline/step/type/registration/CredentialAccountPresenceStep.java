package me.whereareiam.identica.provider.credential.pipeline.step.type.registration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
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
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialAttemptStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Singleton
public class CredentialAccountPresenceStep extends AbstractCredentialAttemptStep {
	private final Provider<CredentialMessages> messagesProvider;
	private final CredentialAccountService credentialService;
	private final CryptographyService cryptographyService;

	@Inject
	public CredentialAccountPresenceStep(
			Provider<CredentialMessages> messagesProvider,
			Provider<Engine> coreSettingsProvider,
			CredentialAccountService credentialService,
			CryptographyService cryptographyService,
			EventManager eventManager
	) {
		super("password-credential-presence", coreSettingsProvider, eventManager);
		this.messagesProvider = messagesProvider;
		this.credentialService = credentialService;
		this.cryptographyService = cryptographyService;
	}

	@Override
	public int order() {
		return 5;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		String providerSubject = requireProviderSubject(context);

		CredentialAccount existing = credentialService.find(providerSubject).orElse(null);
		if (existing == null)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		return CompletableFuture.completedFuture(handleExisting(context, state, existing));
	}

	private StepResult handleExisting(
			@NotNull ScenarioContext context,
			@NotNull PipelineState state,
			@NotNull CredentialAccount credential
	) {
		CredentialMessages messages = messagesProvider.get();
		var input = consumeAuthenticationAttempt(state);
		if (input == null)
			return StepResult.waiting(joinAlreadyRegistered(messages));

		if (!cryptographyService.verify(credential, input.getPassword())) {
			AuthenticationAttemptDecision decision = recordBruteForceDecision(credential, context);
			if (decision.isDeny())
				return StepResult.denied(decision.getDenyMessage());
			return invalidWithWarning(messages, decision.getWarningMessage());
		}

		clearBruteForce(credential, context);
		return StepResult.complete(context);
	}

	private @NotNull String joinAlreadyRegistered(@NotNull CredentialMessages messages) {
		String registered = messages.getScenario().getRegistration().getStatus().getAlreadyRegistered();
		String prompt = joinLines(messages.getScenario().getAuthentication().getPrompt());
		if (registered == null || registered.isBlank())
			return prompt;
		return registered + "\n" + prompt;
	}

	private @NotNull StepResult invalidWithWarning(
			@NotNull CredentialMessages messages,
			String warning
	) {
		String invalid = messages.getScenario().getAuthentication().getStatus().getInvalid();
		if (warning == null || warning.isBlank()) return StepResult.waiting(invalid);
		if (invalid == null || invalid.isBlank()) return StepResult.waiting(warning);
		return StepResult.waiting(invalid + "\n" + warning);
	}
}
