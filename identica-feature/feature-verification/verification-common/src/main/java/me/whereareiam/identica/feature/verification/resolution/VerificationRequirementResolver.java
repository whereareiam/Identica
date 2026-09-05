package me.whereareiam.identica.feature.verification.resolution;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.verification.VerificationMethod;
import me.whereareiam.identica.feature.verification.VerificationPolicyResolver;
import me.whereareiam.identica.feature.verification.VerificationRegistry;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeLifecycle;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.challenge.PendingVerificationChallenge;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.provider.ProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VerificationRequirementResolver {
	private final VerificationPersistenceService persistenceService;
	private final VerificationChallengeStore challengeStore;
	private final VerificationPolicyResolver policyResolver;
	private final VerificationRegistry methodRegistry;
	private final ProviderManager providerManager;
	private final VerificationChallengeLifecycle challengeLifecycle;

	public @NotNull VerificationResolutionResult resolveVerification(@NotNull VerificationResolutionRequest request) {
		String providerId = request.getProviderId();
		VerificationPolicyResolver.ResolvedProviderPolicy providerPolicy = policyResolver.resolveProviderPolicy(providerId);
		if (!supportsVerification(providerId))
			return VerificationResolutionResult.of(VerificationResolutionStatus.SKIPPED, null, null, false, false);
		if (providerPolicy == null || !providerPolicy.enabled())
			return VerificationResolutionResult.of(VerificationResolutionStatus.SKIPPED, null, null, false, false);

		if (challengeStore.consumeVerified(request.getUniqueId(), providerId, request.getPurpose()))
			return VerificationResolutionResult.of(
						VerificationResolutionStatus.SATISFIED,
						null,
						null,
					providerPolicy.required(),
					false
			);

		VerificationSelection selection = persistenceService.findSelection(request.getUniqueId(), providerId).orElse(null);
		if (selection == null || selection.getMethodId() == null || selection.getMethodId().isBlank()) {
			VerificationResolutionStatus status = providerPolicy.required()
					? VerificationResolutionStatus.DENIED
					: VerificationResolutionStatus.SKIPPED;
			return VerificationResolutionResult.of(status, null, null, providerPolicy.required(), false);
		}

		VerificationPolicyResolver.ResolvedMethodPolicy methodPolicy =
				policyResolver.resolveMethodPolicy(providerId, selection.getMethodId());
		if (methodPolicy == null || !methodPolicy.enabled())
			return handleResolutionUnavailable(
					request.getUniqueId(),
					providerId,
					selection.getMethodId(),
					providerPolicy,
					methodPolicy
			);

		List<VerificationEnrollment> enrollments = persistenceService.findEnrollments(request.getUniqueId()).stream()
				.filter(enrollment -> enrollment != null && selection.getMethodId().equalsIgnoreCase(enrollment.getMethodId()))
				.toList();
		if (enrollments.isEmpty())
			return handleResolutionUnavailable(
					request.getUniqueId(),
					providerId,
					selection.getMethodId(),
					providerPolicy,
					methodPolicy
			);

		VerificationMethod method = methodRegistry.find(selection.getMethodId()).orElse(null);
		if (method == null)
			return handleResolutionUnavailable(
					request.getUniqueId(),
					providerId,
					selection.getMethodId(),
					providerPolicy,
					methodPolicy
			);

		PendingVerificationChallenge existing = challengeStore.findActive(
				request.getUniqueId(),
				providerId,
				request.getPurpose()
		).orElse(null);
		if (existing != null)
			return VerificationResolutionResult.of(
						VerificationResolutionStatus.WAITING,
						existing.getChallengeId(),
						existing.getMethodId(),
					methodPolicy.required(),
					false
			);

		VerificationChallengeResult<?> challenge = challengeLifecycle.startChallenge(
				request.getUniqueId(),
				providerId,
				request.getPurpose(),
				enrollments,
				method,
				methodPolicy.required()
		);

		return VerificationResolutionResult.of(
				toResolutionStatus(challenge.getStatus()),
				challenge.getChallengeId(),
				challenge.getMethodId(),
				challenge.isRequired(),
				challenge.isRecoveryCodeUsed()
		);
	}

	private @NotNull VerificationResolutionResult handleResolutionUnavailable(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@NotNull String methodId,
			@NotNull VerificationPolicyResolver.ResolvedProviderPolicy providerPolicy,
			@Nullable VerificationPolicyResolver.ResolvedMethodPolicy methodPolicy
	) {
		UnavailableSelectionPolicy unavailablePolicy = methodPolicy != null
				? methodPolicy.unavailableSelectionPolicy()
				: providerPolicy.unavailableSelectionPolicy();
		if (unavailablePolicy == null)
			unavailablePolicy = UnavailableSelectionPolicy.CLEAR_SELECTION;
		if (unavailablePolicy == UnavailableSelectionPolicy.CLEAR_SELECTION)
			persistenceService.deleteSelection(uniqueId, providerId);

		boolean required = methodPolicy != null ? methodPolicy.required() : providerPolicy.required();
		VerificationResolutionStatus status = required
				? VerificationResolutionStatus.DENIED
				: VerificationResolutionStatus.SKIPPED;
		return VerificationResolutionResult.of(status, null, methodId, required, false);
	}

	private @NotNull VerificationResolutionStatus toResolutionStatus(@NotNull VerificationChallengeStatus status) {
		if (status == VerificationChallengeStatus.VERIFIED) return VerificationResolutionStatus.SATISFIED;
		if (status == VerificationChallengeStatus.WAITING || status == VerificationChallengeStatus.INVALID)
			return VerificationResolutionStatus.WAITING;

		if (status == VerificationChallengeStatus.PROVIDER_UNSUPPORTED
				|| status == VerificationChallengeStatus.PROVIDER_VERIFICATION_DISABLED)
			return VerificationResolutionStatus.SKIPPED;

		return VerificationResolutionStatus.DENIED;
	}

	private boolean supportsVerification(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return false;

		return providerManager.getProviders().stream()
				.anyMatch(provider -> provider != null
						&& provider.getDescriptor() != null
						&& providerId.equalsIgnoreCase(provider.getDescriptor().getId())
						&& provider.getDescriptor().supportsFeature("verification"));
	}
}
