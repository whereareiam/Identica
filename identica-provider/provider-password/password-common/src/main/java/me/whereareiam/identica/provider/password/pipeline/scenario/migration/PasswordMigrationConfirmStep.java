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
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegistrationAttempt;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegisterStateItem;
import me.whereareiam.identica.provider.password.pipeline.scenario.AbstractPasswordStep;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordMigrationConfirmStep extends AbstractPasswordStep {
	private final Provider<PasswordMessages> messagesProvider;
	private final Provider<Settings> coreSettingsProvider;
	private final PasswordAccountService accountService;
	private final PipelineStateStore pipelineStateStore;
	private final CryptographyService cryptographyService;

	@Inject
	public PasswordMigrationConfirmStep(
			Provider<PasswordMessages> messagesProvider,
			Provider<Settings> coreSettingsProvider,
			PasswordAccountService accountService,
			PipelineStateStore pipelineStateStore,
			CryptographyService cryptographyService
	) {
		super("password-migration-confirm");
		this.messagesProvider = messagesProvider;
		this.coreSettingsProvider = coreSettingsProvider;
		this.accountService = accountService;
		this.pipelineStateStore = pipelineStateStore;
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

		long ttlMs = migrationTtlMs();
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

	private long migrationTtlMs() {
		Settings settings = coreSettingsProvider.get();
		if (settings == null)
			return 0L;
		return settings.getConnection().getMigration().pipelineTtlMillis();
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

	private @NotNull PipelineStateReference reference(@NotNull ScenarioContext context) {
		return PipelineStateReference.from(context);
	}

	private @Nullable PasswordRegisterStateItem getRegisterState(@NotNull ScenarioContext context) {
		PipelineState stored = pipelineStateStore.find(reference(context)).orElse(null);
		return stored != null ? stored.item(PasswordRegisterStateItem.class).orElse(null) : null;
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
