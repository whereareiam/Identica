package me.whereareiam.identica.provider.password.pipeline.scenario.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegisterStateItem;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegistrationAttempt;
import me.whereareiam.identica.provider.password.pipeline.scenario.AbstractPasswordStep;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import me.whereareiam.identica.provider.password.util.PasswordRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordMigrationRegistrationStep extends AbstractPasswordStep {
	private final Provider<PasswordMessages> messagesProvider;
	private final Provider<PasswordSettings> settingsProvider;
	private final Provider<Settings> coreSettingsProvider;
	private final PasswordAccountService accountService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;
	private final PipelineStateStore pipelineStateStore;

	@Inject
	public PasswordMigrationRegistrationStep(
			Provider<PasswordMessages> messagesProvider,
			Provider<PasswordSettings> settingsProvider,
			Provider<Settings> coreSettingsProvider,
			PasswordAccountService accountService,
			CryptographyService cryptographyService,
			PasswordRules passwordPolicy,
			PipelineStateStore pipelineStateStore
	) {
		super("password-migration-registration");
		this.messagesProvider = messagesProvider;
		this.settingsProvider = settingsProvider;
		this.coreSettingsProvider = coreSettingsProvider;
		this.accountService = accountService;
		this.cryptographyService = cryptographyService;
		this.passwordPolicy = passwordPolicy;
		this.pipelineStateStore = pipelineStateStore;
	}

	@Override
	public int order() {
		return 15;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String providerSubject = requireProviderSubject(context);

		PasswordAccount account = accountService.find(providerSubject).orElse(null);
		if (account != null)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		PasswordMessages messages = messagesProvider.get();
		PasswordSettings settings = settingsProvider.get();
		PasswordSettings.Scenario.Registration registrationSettings = settings != null
				&& settings.getScenario() != null
				? settings.getScenario().getRegistration()
				: null;

		if (registrationSettings == null || !registrationSettings.isEnabled())
			return CompletableFuture.completedFuture(StepResult.denied(messages.getScenario().getRegistration().getStatus().getDisabled()));

		long ttlMs = migrationTtlMs();
		boolean requireRepeat = registrationSettings.isRequireRepeat();
		PasswordRegisterStateItem pending = getRegisterState(context);

		if (requireRepeat && pending != null)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		if (!requireRepeat && pending != null)
			clearRegisterState(context, ttlMs);

		PasswordRegistrationAttempt input = consumeRegistrationAttempt(context, ttlMs);
		if (input == null)
			return CompletableFuture.completedFuture(StepResult.waiting(joinLines(messages.getScenario().getRegistration().getPrompt())));

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

	private long migrationTtlMs() {
		Settings settings = coreSettingsProvider.get();
		if (settings == null)
			return 0L;
		return settings.getConnection().getMigration().pipelineTtlMillis();
	}

	private @NotNull PipelineStateReference reference(@NotNull ScenarioContext context) {
		return PipelineStateReference.from(context);
	}

	private @Nullable PasswordRegistrationAttempt consumeRegistrationAttempt(
			@NotNull ScenarioContext context,
			long ttlMs
	) {
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return null;

		PasswordRegistrationAttempt input = stored.item(PasswordRegistrationAttempt.class).orElse(null);
		if (input == null) return null;

		stored.removeItem(PasswordRegistrationAttempt.class);
		if (ttlMs > 0) {
			pipelineStateStore.save(reference, stored, ttlMs);
		}
		return input;
	}

	private @Nullable PasswordRegisterStateItem getRegisterState(@NotNull ScenarioContext context) {
		PipelineState stored = pipelineStateStore.find(reference(context)).orElse(null);
		return stored != null ? stored.item(PasswordRegisterStateItem.class).orElse(null) : null;
	}

	private void storeRegisterState(
			@NotNull ScenarioContext context,
			@NotNull PasswordRegisterStateItem stateItem,
			long ttlMs
	) {
		if (ttlMs <= 0) return;
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		stored.putItem(stateItem, ttlMs);
		pipelineStateStore.save(reference, stored, ttlMs);
	}

	private void clearRegisterState(@NotNull ScenarioContext context, long ttlMs) {
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		stored.removeItem(PasswordRegisterStateItem.class);
		if (ttlMs > 0) {
			pipelineStateStore.save(reference, stored, ttlMs);
		}
	}

	private static @NotNull String joinLines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty())
			return "";
		return String.join("\n", lines);
	}
}
