package me.whereareiam.identica.provider.password.pipeline.scenario.registration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegistrationAttempt;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegisterStateItem;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordRegistrationConfirmStep extends AbstractPasswordRegistrationStep {
	private final PasswordAccountService accountService;
	private final CryptographyService cryptographyService;

	@Inject
	public PasswordRegistrationConfirmStep(
			Provider<PasswordMessages> messagesProvider,
			Provider<Settings> coreSettingsProvider,
			PasswordAccountService accountService,
			PipelineStateStore pipelineStateStore,
			CryptographyService cryptographyService
	) {
		super("password-registration-confirm", messagesProvider, coreSettingsProvider, pipelineStateStore);
		this.accountService = accountService;
		this.cryptographyService = cryptographyService;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String providerSubject = requireProviderSubject(context);

		PasswordMessages messages = messagesProvider.get();
		PasswordRegisterStateItem pending = getRegisterState(context);
		if (pending == null)
			return CompletableFuture.completedFuture(StepResult.waiting(joinRegisterPrompt(messages)));

		long ttlMs = registrationTtlMs();
		PasswordRegistrationAttempt input = consumeRegistrationAttempt(context, ttlMs);
		if (input == null)
			return CompletableFuture.completedFuture(StepResult.waiting(joinConfirmPrompt(messages)));

		if (!input.isConfirm()) {
			clearRegisterState(context, ttlMs);
			return CompletableFuture.completedFuture(StepResult.waiting(joinRegisterReset(messages)));
		}

		if (!matchesPending(input, pending)) {
			clearRegisterState(context, ttlMs);
			return CompletableFuture.completedFuture(StepResult.waiting(joinRegisterReset(messages)));
		}

		PasswordAccount account = accountService.register(
				providerSubject,
				pending.getPasswordHash(),
				pending.getHashingMethod(),
				PasswordChangeReason.REGISTER
		).orElse(null);
		clearRegisterState(context, ttlMs);
		if (account == null)
			return CompletableFuture.completedFuture(StepResult.failed(""));

		return CompletableFuture.completedFuture(StepResult.complete(context));
	}

	private boolean matchesPending(PasswordRegistrationAttempt input, PasswordRegisterStateItem pending) {
		if (input == null || pending == null)
			return false;
		return cryptographyService.verify(
				input.getPassword(),
				pending.getPasswordHash(),
				pending.getHashingMethod()
		);
	}

	private String joinRegisterPrompt(PasswordMessages messages) {
		return joinLines(messages.getScenario().getRegistration().getPrompt());
	}

	private String joinConfirmPrompt(PasswordMessages messages) {
		return joinLines(messages.getScenario().getRegistration().getConfirmPrompt());
	}

	private String joinRegisterReset(PasswordMessages messages) {
		String mismatch = messages.getScenario().getRegistration().getStatus().getMismatch();
		String prompt = joinRegisterPrompt(messages);
		if (mismatch == null || mismatch.isBlank())
			return prompt;
		return mismatch + "\n" + prompt;
	}

}
