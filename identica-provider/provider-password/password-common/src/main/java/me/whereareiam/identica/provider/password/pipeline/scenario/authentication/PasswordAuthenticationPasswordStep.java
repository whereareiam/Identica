package me.whereareiam.identica.provider.password.pipeline.scenario.authentication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.event.authentication.AuthenticationAttemptDecision;
import me.whereareiam.identica.provider.password.event.authentication.AuthenticationAttemptFailedEvent;
import me.whereareiam.identica.provider.password.event.authentication.AuthenticationAttemptSucceededEvent;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.model.authentication.AuthenticationAttemptContext;
import me.whereareiam.identica.provider.password.pipeline.PasswordAuthenticationAttempt;
import me.whereareiam.identica.provider.password.pipeline.scenario.AbstractPasswordStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordAuthenticationPasswordStep extends AbstractPasswordStep {
	private final Provider<PasswordMessages> messagesProvider;
	private final Provider<Settings> coreSettingsProvider;
	private final PasswordAccountService accountService;
	private final CryptographyService cryptographyService;
	private final PipelineStateStore pipelineStateStore;
	private final EventManager eventManager;

	@Inject
	public PasswordAuthenticationPasswordStep(
			Provider<PasswordMessages> messagesProvider,
			Provider<Settings> coreSettingsProvider,
			PasswordAccountService accountService,
			CryptographyService cryptographyService,
			PipelineStateStore pipelineStateStore,
			EventManager eventManager
	) {
		super("password-authentication-password");
		this.messagesProvider = messagesProvider;
		this.coreSettingsProvider = coreSettingsProvider;
		this.accountService = accountService;
		this.cryptographyService = cryptographyService;
		this.pipelineStateStore = pipelineStateStore;
		this.eventManager = eventManager;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String providerSubject = requireProviderSubject(context);

		PasswordMessages messages = messagesProvider.get();
		PasswordAccount account = accountService.find(providerSubject).orElse(null);
		if (account == null) return CompletableFuture.completedFuture(StepResult.denied(messages.getScenario().getAuthentication().getStatus().getNotRegistered()));

		long ttlMs = authenticationTtlMs();
		PasswordAuthenticationAttempt input = consumeAuthenticationAttempt(context, ttlMs);
		if (input == null) return CompletableFuture.completedFuture(StepResult.waiting(joinLines(messages.getScenario().getAuthentication().getPrompt())));

		if (!cryptographyService.verify(account, input.getPassword())) {
			AuthenticationAttemptDecision decision = recordBruteForceDecision(account, context);
			if (decision.isDeny()) return CompletableFuture.completedFuture(StepResult.denied(decision.getDenyMessage()));
			return CompletableFuture.completedFuture(invalidWithWarning(messages, decision.getWarningMessage()));
		}

		clearBruteForce(account, context);
		return CompletableFuture.completedFuture(StepResult.proceed(context));
	}

	private @NotNull AuthenticationAttemptDecision recordBruteForceDecision(
			@NotNull PasswordAccount account,
			@NotNull ScenarioContext context
	) {
		AuthenticationAttemptFailedEvent event = new AuthenticationAttemptFailedEvent(
				attemptContext(account, context),
				null
		);
		eventManager.call(event);
		AuthenticationAttemptDecision decision = event.getDecision();
		return decision != null ? decision : AuthenticationAttemptDecision.allow();
	}

	private void clearBruteForce(
			@NotNull PasswordAccount account,
			@NotNull ScenarioContext context
	) {
		eventManager.call(new AuthenticationAttemptSucceededEvent(attemptContext(account, context)));
	}

	private @NotNull AuthenticationAttemptContext attemptContext(
			@NotNull PasswordAccount account,
			@NotNull ScenarioContext context
	) {
		return new AuthenticationAttemptContext(
				account,
				context.getConnectionUniqueId(),
				context.getIdenticaUniqueId(),
				context.getUsername(),
				context.getIp()
		);
	}

	private long authenticationTtlMs() {
		Settings settings = coreSettingsProvider.get();
		if (settings == null)
			return 0L;
		return settings.getConnection().getAuthentication().pipelineTtlMillis();
	}

	private @NotNull PipelineStateReference reference(@NotNull ScenarioContext context) {
		return PipelineStateReference.from(context);
	}

	private @Nullable PasswordAuthenticationAttempt consumeAuthenticationAttempt(
			@NotNull ScenarioContext context,
			long ttlMs
	) {
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return null;

		PasswordAuthenticationAttempt input = stored.item(PasswordAuthenticationAttempt.class).orElse(null);
		if (input == null) return null;

		stored.removeItem(PasswordAuthenticationAttempt.class);
		if (ttlMs > 0) {
			pipelineStateStore.save(reference, stored, ttlMs);
		}
		return input;
	}

	private static @NotNull String joinLines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty()) return "";
		return String.join("\n", lines);
	}

	private StepResult invalidWithWarning(PasswordMessages messages, String warning) {
		String invalid = messages.getScenario().getAuthentication().getStatus().getInvalid();
		if (warning == null || warning.isBlank()) return StepResult.waiting(invalid);
		if (invalid == null || invalid.isBlank()) return StepResult.waiting(warning);

		return StepResult.waiting(invalid + "\n" + warning);
	}
}
