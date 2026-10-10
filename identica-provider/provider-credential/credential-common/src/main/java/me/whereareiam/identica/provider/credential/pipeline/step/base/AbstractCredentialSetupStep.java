package me.whereareiam.identica.provider.credential.pipeline.step.base;

import com.google.inject.Provider;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegisterStateItem;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractCredentialSetupStep extends AbstractCredentialStatefulStep {
	protected final Provider<CredentialMessages> messagesProvider;
	private final Provider<CredentialSettings> settingsProvider;
	private final CredentialAccountService credentialService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;

	protected AbstractCredentialSetupStep(
			@NotNull String name,
			@NotNull Provider<CredentialMessages> messagesProvider,
			@NotNull Provider<CredentialSettings> settingsProvider,
			@NotNull Provider<Engine> coreSettingsProvider,
			@NotNull CredentialAccountService credentialService,
			@NotNull CryptographyService cryptographyService,
			@NotNull PasswordRules passwordPolicy
	) {
		super(name, coreSettingsProvider);
		this.messagesProvider = messagesProvider;
		this.settingsProvider = settingsProvider;
		this.credentialService = credentialService;
		this.cryptographyService = cryptographyService;
		this.passwordPolicy = passwordPolicy;
	}

	@Override
	public final @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		String providerSubject = requireProviderSubject(context);

		CredentialAccount existing = credentialService.find(providerSubject).orElse(null);
		StepResult existingResult = existingCredentialResult(context, existing);
		if (existingResult != null) return CompletableFuture.completedFuture(existingResult);

		CredentialMessages messages = messagesProvider.get();
		CredentialSettings settings = settingsProvider.get();
		CredentialSettings.Scenario.Registration registrationSettings = settings != null
				&& settings.getScenario() != null
				? settings.getScenario().getRegistration()
				: null;

		if (registrationSettings == null || !registrationSettings.isEnabled())
			return CompletableFuture.completedFuture(StepResult.denied(disabledMessage(messages)));

		boolean requireRepeat = registrationSettings.isRequireRepeat();
		CredentialRegisterStateItem pending = getRegisterState(state);
		long ttlMs = ttlMs();

		if (requireRepeat && pending != null) return CompletableFuture.completedFuture(StepResult.proceed(context));
		if (!requireRepeat && pending != null) clearRegisterState(state);

		var input = consumeRegistrationAttempt(state);
		if (input == null) return CompletableFuture.completedFuture(StepResult.waiting(registerPrompt(messages)));
		if (input.isConfirm()) return CompletableFuture.completedFuture(StepResult.waiting(noPendingMessage(messages)));

		String error = passwordPolicy.validate(input.getPassword());
		if (error != null && !error.isBlank())
			return CompletableFuture.completedFuture(StepResult.waiting(error));

		PasswordCandidate candidate = cryptographyService.hash(input.getPassword());
		if (candidate == null) return CompletableFuture.completedFuture(StepResult.failed(""));

		if (requireRepeat) {
			storeRegisterState(state, new CredentialRegisterStateItem(
					candidate.getPasswordHash(),
					candidate.getHashingMethod()
			), ttlMs);
			return CompletableFuture.completedFuture(StepResult.proceed(context));
		}

		if (credentialService.register(
				providerSubject,
				candidate.getPasswordHash(),
				candidate.getHashingMethod(),
				PasswordChangeReason.REGISTER
		).isEmpty()) {
			return CompletableFuture.completedFuture(StepResult.failed(""));
		}

		return CompletableFuture.completedFuture(StepResult.complete(context));
	}

	protected abstract long ttlMs();

	protected abstract @Nullable StepResult existingCredentialResult(
			@NotNull ScenarioContext context,
			@Nullable CredentialAccount credential
	);

	protected abstract @NotNull String disabledMessage(@NotNull CredentialMessages messages);

	protected abstract @NotNull String registerPrompt(@NotNull CredentialMessages messages);

	protected abstract @NotNull String noPendingMessage(@NotNull CredentialMessages messages);
}
