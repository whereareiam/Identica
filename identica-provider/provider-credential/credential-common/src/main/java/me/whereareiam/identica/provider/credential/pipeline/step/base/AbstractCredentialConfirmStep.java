package me.whereareiam.identica.provider.credential.pipeline.step.base;

import com.google.inject.Provider;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegisterStateItem;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractCredentialConfirmStep extends AbstractCredentialStatefulStep {
	protected final Provider<CredentialMessages> messagesProvider;
	private final CredentialAccountService credentialService;
	private final CryptographyService cryptographyService;

	protected AbstractCredentialConfirmStep(
			@NotNull String name,
			@NotNull Provider<CredentialMessages> messagesProvider,
			@NotNull Provider<Engine> coreSettingsProvider,
			@NotNull CredentialAccountService credentialService,
			@NotNull CryptographyService cryptographyService
	) {
		super(name, coreSettingsProvider);
		this.messagesProvider = messagesProvider;
		this.credentialService = credentialService;
		this.cryptographyService = cryptographyService;
	}

	@Override
	public final @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		String providerSubject = requireProviderSubject(context);

		CredentialMessages messages = messagesProvider.get();
		CredentialRegisterStateItem pending = getRegisterState(state);
		if (pending == null) return CompletableFuture.completedFuture(StepResult.waiting(registerPrompt(messages)));

		var input = consumeRegistrationAttempt(state);
		if (input == null) return CompletableFuture.completedFuture(StepResult.waiting(confirmPrompt(messages)));

		if (!input.isConfirm()) {
			clearRegisterState(state);
			return CompletableFuture.completedFuture(StepResult.waiting(registerReset(messages)));
		}

		if (!matchesPending(input.getPassword(), pending)) {
			clearRegisterState(state);
			return CompletableFuture.completedFuture(StepResult.waiting(registerReset(messages)));
		}

		boolean registered = credentialService.register(
				providerSubject,
				pending.getPasswordHash(),
				pending.getHashingMethod(),
				PasswordChangeReason.REGISTER
		).isPresent();
		clearRegisterState(state);
		if (!registered) return CompletableFuture.completedFuture(StepResult.failed(""));

		return CompletableFuture.completedFuture(StepResult.complete(context));
	}

	protected abstract @NotNull String registerPrompt(@NotNull CredentialMessages messages);

	protected abstract @NotNull String confirmPrompt(@NotNull CredentialMessages messages);

	protected abstract @NotNull String mismatchMessage(@NotNull CredentialMessages messages);

	private boolean matchesPending(
			@NotNull String candidate,
			@NotNull CredentialRegisterStateItem pending
	) {
		return cryptographyService.verify(
				candidate,
				pending.getPasswordHash(),
				pending.getHashingMethod()
		);
	}

	private @NotNull String registerReset(@NotNull CredentialMessages messages) {
		String mismatch = mismatchMessage(messages);
		String prompt = registerPrompt(messages);
		if (mismatch.isBlank()) return prompt;

		return mismatch + "\n" + prompt;
	}
}
