package me.whereareiam.identica.provider.password.pipeline.scenario.authentication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.verification.VerificationResolutionRequest;
import me.whereareiam.identica.model.verification.VerificationResolutionResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.pipeline.scenario.AbstractPasswordStep;
import me.whereareiam.identica.type.verification.VerificationResolutionStatus;
import me.whereareiam.identica.verification.VerificationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordAuthenticationVerificationStep extends AbstractPasswordStep {
	private final Provider<PasswordMessages> messagesProvider;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final VerificationService verificationService;

	@Inject
	public PasswordAuthenticationVerificationStep(
			Provider<PasswordMessages> messagesProvider,
			ProviderLinkPersistenceService providerLinkPersistenceService,
			VerificationService verificationService
	) {
		super("password-authentication-verification");
		this.messagesProvider = messagesProvider;
		this.providerLinkPersistenceService = providerLinkPersistenceService;
		this.verificationService = verificationService;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String providerSubject = requireProviderSubject(context);
		UUID uniqueId = providerLinkPersistenceService.findBySubject(PasswordConstants.PROVIDER_ID, providerSubject)
				.map(AccountProviderLink::getUniqueId)
				.orElse(null);
		if (uniqueId == null) {
			Logger.debug(
					"Password verification skipped missing provider link connection=%s username=%s subject=%s",
					context.getConnectionUniqueId(),
					context.getUsername(),
					providerSubject
			);
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		VerificationResolutionResult result = verificationService.resolveVerification(VerificationResolutionRequest.builder()
				.uniqueId(uniqueId)
				.providerId(PasswordConstants.PROVIDER_ID)
				.purpose("authentication")
				.build());
		Logger.debug(
				"Password verification resolved connection=%s username=%s uniqueId=%s status=%s method=%s challenge=%s required=%s",
				context.getConnectionUniqueId(),
				context.getUsername(),
				uniqueId,
				result.getStatus(),
				result.getMethodId(),
				result.getChallengeId(),
				result.isRequired()
		);

		PasswordMessages.Scenario.Authentication.Verification messages = messagesProvider.get()
				.getScenario()
				.getAuthentication()
				.getVerification();

		return CompletableFuture.completedFuture(toStepResult(result, context, messages));
	}

	private StepResult toStepResult(
			@NotNull VerificationResolutionResult result,
			@NotNull ScenarioContext context,
			@NotNull PasswordMessages.Scenario.Authentication.Verification messages
	) {
		VerificationResolutionStatus status = result.getStatus();
		if (status == VerificationResolutionStatus.SATISFIED || status == VerificationResolutionStatus.SKIPPED)
			return StepResult.complete(context);

		if (status == VerificationResolutionStatus.WAITING)
			return StepResult.waiting(joinLines(messages.getPrompt()));

		if (status == VerificationResolutionStatus.DENIED)
			return StepResult.denied(result.getMethodId() == null
					? messages.getRequired()
					: messages.getUnavailable());

		return StepResult.failed("");
	}

	private static @NotNull String joinLines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty()) return "";
		return String.join("\n", lines);
	}
}
