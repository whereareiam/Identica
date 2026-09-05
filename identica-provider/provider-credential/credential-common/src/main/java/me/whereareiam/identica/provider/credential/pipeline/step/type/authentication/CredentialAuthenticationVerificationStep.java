package me.whereareiam.identica.provider.credential.pipeline.step.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class CredentialAuthenticationVerificationStep extends AbstractCredentialStep {
	private final Provider<CredentialMessages> messagesProvider;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	private final @NotNull FeatureRegistry features;
	private final @NotNull Injector injector;

	@Inject
	public CredentialAuthenticationVerificationStep(
			Provider<CredentialMessages> messagesProvider,
			ProviderLinkPersistenceService providerLinkPersistenceService,
			@NotNull FeatureRegistry features,
			@NotNull Injector injector
	) {
		super("password-authentication-verification");
		this.features = features;
		this.injector = injector;
		this.messagesProvider = messagesProvider;
		this.providerLinkPersistenceService = providerLinkPersistenceService;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		if (!features.isEnabled("credential", "verification"))
			return CompletableFuture.completedFuture(StepResult.complete(context));

		String providerSubject = requireProviderSubject(context);
		UUID uniqueId = providerLinkPersistenceService.findBySubject(CredentialConstants.PROVIDER_ID, providerSubject)
				.map(AccountProviderLink::getUniqueId)
				.orElse(null);
		if (uniqueId == null) {
			Logger.debug(
					"Credential verification skipped missing provider link connection=%s username=%s subject=%s",
					context.getConnectionUniqueId(),
					context.getUsername(),
					providerSubject
			);
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		VerificationResolutionResult result = injector.getInstance(VerificationService.class).resolveVerification(VerificationResolutionRequest.builder()
				.uniqueId(uniqueId)
				.providerId(CredentialConstants.PROVIDER_ID)
				.purpose("authentication")
				.build());
		Logger.debug(
				"Credential verification resolved connection=%s username=%s uniqueId=%s status=%s method=%s challenge=%s required=%s",
				context.getConnectionUniqueId(),
				context.getUsername(),
				uniqueId,
				result.getStatus(),
				result.getMethodId(),
				result.getChallengeId(),
				result.isRequired()
		);

		CredentialMessages.Scenario.Authentication.Verification messages = messagesProvider.get()
				.getScenario()
				.getAuthentication()
				.getVerification();

		return CompletableFuture.completedFuture(toStepResult(result, context, messages));
	}

	private StepResult toStepResult(
			@NotNull VerificationResolutionResult result,
			@NotNull ScenarioContext context,
			@NotNull CredentialMessages.Scenario.Authentication.Verification messages
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
