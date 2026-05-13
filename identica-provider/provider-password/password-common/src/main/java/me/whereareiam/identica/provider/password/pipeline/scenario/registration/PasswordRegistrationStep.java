package me.whereareiam.identica.provider.password.pipeline.scenario.registration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.util.PasswordRules;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegistrationAttempt;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegisterStateItem;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordRegistrationStep extends AbstractPasswordRegistrationStep {
	private final Provider<PasswordSettings> settingsProvider;
	private final PasswordAccountService accountService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;

	@Inject
	public PasswordRegistrationStep(
			Provider<PasswordMessages> messagesProvider,
			Provider<PasswordSettings> settingsProvider,
			Provider<Settings> coreSettingsProvider,
			PasswordAccountService accountService,
			CryptographyService cryptographyService,
			PasswordRules passwordPolicy,
			PipelineStateStore pipelineStateStore
	) {
		super("password-registration", messagesProvider, coreSettingsProvider, pipelineStateStore);
		this.settingsProvider = settingsProvider;
		this.accountService = accountService;
		this.cryptographyService = cryptographyService;
		this.passwordPolicy = passwordPolicy;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String providerSubject = requireProviderSubject(context);

		PasswordSettings settings = settingsProvider.get();
		PasswordMessages messages = messagesProvider.get();
		PasswordSettings.Scenario.Registration registrationSettings = settings != null
				&& settings.getScenario() != null
				? settings.getScenario().getRegistration()
				: null;

		if (registrationSettings == null || !registrationSettings.isEnabled()) {
			return CompletableFuture.completedFuture(StepResult.denied(messages.getScenario().getRegistration().getStatus().getDisabled()));
		}

		boolean requireRepeat = registrationSettings.isRequireRepeat();
		PasswordRegisterStateItem pending = getRegisterState(context);
		long ttlMs = registrationTtlMs();

		if (requireRepeat && pending != null)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		if (!requireRepeat && pending != null)
			clearRegisterState(context, ttlMs);

		PasswordRegistrationAttempt input = consumeRegistrationAttempt(context, ttlMs);

		if (input == null)
			return CompletableFuture.completedFuture(StepResult.waiting(joinRegisterPrompt(messages)));

		if (input.isConfirm())
			return CompletableFuture.completedFuture(StepResult.waiting(messages.getScenario().getRegistration().getStatus().getNoPending()));

		String error = passwordPolicy.validate(input.getPassword());
		if (error != null && !error.isBlank())
			return CompletableFuture.completedFuture(StepResult.waiting(error));

		PasswordCandidate candidate = cryptographyService.hash(input.getPassword());
		if (candidate == null)
			return CompletableFuture.completedFuture(StepResult.failed(""));

		if (requireRepeat) {
			PasswordRegisterStateItem stateItem = new PasswordRegisterStateItem(
					candidate.getPasswordHash(),
					candidate.getHashingMethod()
			);
			storeRegisterState(context, stateItem, ttlMs);
			return CompletableFuture.completedFuture(StepResult.proceed(context));
		}

		if (accountService.register(
				providerSubject,
				candidate.getPasswordHash(),
				candidate.getHashingMethod(),
				PasswordChangeReason.REGISTER
		).isEmpty()) {
			return CompletableFuture.completedFuture(StepResult.failed(""));
		}

		return CompletableFuture.completedFuture(StepResult.complete(context));
	}

	private String joinRegisterPrompt(PasswordMessages messages) {
		return joinLines(messages.getScenario().getRegistration().getPrompt());
	}

}
