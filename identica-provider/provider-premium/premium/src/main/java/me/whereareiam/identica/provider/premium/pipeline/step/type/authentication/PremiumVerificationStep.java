package me.whereareiam.identica.provider.premium.pipeline.step.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationCodeAttempt;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.StatefulStep;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.journey.step.type.InteractiveStep;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PremiumVerificationStep extends InteractiveStep implements StatefulStep {
	private final Provider<PremiumMessages> messagesProvider;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	private final @NotNull FeatureRegistry features;
	private final @NotNull Injector injector;

	@Inject
	public PremiumVerificationStep(
			Provider<PremiumMessages> messagesProvider,
			ProviderLinkPersistenceService providerLinkPersistenceService,
			@NotNull FeatureRegistry features,
			@NotNull Injector injector
	) {
		super("premium-authentication-verification");
		this.features = features;
		this.injector = injector;
		this.messagesProvider = messagesProvider;
		this.providerLinkPersistenceService = providerLinkPersistenceService;
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		String code = takeCode(state);
		if (!features.isEnabled("premium", "verification"))
			return CompletableFuture.completedFuture(StepResult.complete(context));

		ProviderContext provider = context.getProvider();
		if (provider == null || provider.getProviderId() == null || provider.getProviderSubject() == null)
			return CompletableFuture.completedFuture(StepResult.complete(context));

		UUID uniqueId = providerLinkPersistenceService.findBySubject(provider.getProviderId(), provider.getProviderSubject())
				.map(AccountProviderLink::getUniqueId)
				.orElse(null);
		if (uniqueId == null) {
			Logger.debug(
					"Premium verification skipped missing provider link connection=%s username=%s provider=%s subject=%s",
					context.getConnectionUniqueId(),
					context.getUsername(),
					provider.getProviderId(),
					provider.getProviderSubject()
			);
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		VerificationResolutionResult result = injector.getInstance(VerificationService.class).resolveVerification(VerificationResolutionRequest.builder()
				.uniqueId(uniqueId)
				.providerId(provider.getProviderId())
				.purpose("authentication")
				.code(code)
				.build());
		Logger.debug(
				"Premium verification resolved connection=%s username=%s uniqueId=%s provider=%s status=%s method=%s challenge=%s required=%s",
				context.getConnectionUniqueId(),
				context.getUsername(),
				uniqueId,
				provider.getProviderId(),
				result.getStatus(),
				result.getMethodId(),
				result.getChallengeId(),
				result.isRequired()
		);

		PremiumMessages.Verification.Authentication messages = messagesProvider.get().getVerification().getAuthentication();
		return CompletableFuture.completedFuture(toStepResult(result, context, messages));
	}

	private StepResult toStepResult(
			@NotNull VerificationResolutionResult result,
			@NotNull ScenarioContext context,
			PremiumMessages.Verification.@NotNull Authentication messages
	) {
		VerificationResolutionStatus status = result.getStatus();
		if (status == VerificationResolutionStatus.SATISFIED || status == VerificationResolutionStatus.SKIPPED)
			return StepResult.complete(context);
		if (status == VerificationResolutionStatus.WAITING)
			return StepResult.waiting(joinLines(messages.getPrompt()));
		if (status == VerificationResolutionStatus.REJECTED)
			return StepResult.waiting(rejectionMessage(result));

		if (status == VerificationResolutionStatus.DENIED)
			return StepResult.denied(result.getMethodId() == null
					? messages.getRequired()
					: messages.getUnavailable());

		return StepResult.failed("");
	}

	/**
	 * Takes the code a player typed out of the run's state, so it reaches only this run's resolution.
	 */
	private static @Nullable String takeCode(@NotNull PipelineState state) {
		VerificationCodeAttempt attempt = state.get(VerificationCodeAttempt.KEY).orElse(null);
		if (attempt == null) return null;

		state.remove(VerificationCodeAttempt.KEY);
		return attempt.getCode();
	}

	/**
	 * Answers a code the challenge did not take with the confirm command's messages: a wrong code is invalid, and a
	 * code without a challenge to take it finds nothing pending.
	 */
	private @NotNull String rejectionMessage(@NotNull VerificationResolutionResult result) {
		VerificationMessages.Commands.Confirm messages = injector.getInstance(VerificationMessages.class).getCommands().getConfirm();
		if (result.getRejection() == VerificationChallengeStatus.INVALID) return messages.getInvalidCode();

		return messages.getNoPending();
	}

	private static @NotNull String joinLines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty()) return "";
		return String.join("\n", lines);
	}
}
