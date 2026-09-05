package me.whereareiam.identica.feature.verification;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeLifecycle;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentLifecycle;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentStore;
import me.whereareiam.identica.feature.verification.event.VerificationMethodDisabledEvent;
import me.whereareiam.identica.feature.verification.event.VerificationResetEvent;
import me.whereareiam.identica.feature.verification.event.VerificationSelectionEvent;
import me.whereareiam.identica.feature.verification.model.VerificationDisableResult;
import me.whereareiam.identica.feature.verification.model.VerificationResetResult;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.PendingVerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentResult;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelectionResult;
import me.whereareiam.identica.feature.verification.resolution.VerificationRequirementResolver;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;
import me.whereareiam.identica.feature.verification.type.status.VerificationDisableStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationEnrollmentStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResetStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationSelectionStatus;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.provider.ProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultVerificationService implements VerificationService {
	private final VerificationPersistenceService persistenceService;
	private final VerificationEnrollmentStore enrollmentStore;
	private final Provider<VerificationSettings> verificationProvider;
	private final VerificationRegistry methodRegistry;
	private final ProviderManager providerManager;
	private final VerificationPolicyResolver policyResolver;
	private final SessionService sessionService;
	private final EventManager eventManager;

	private final VerificationRequirementResolver verificationRequirementResolver;
	private final VerificationChallengeLifecycle challengeLifecycle;
	private final VerificationEnrollmentLifecycle enrollmentLifecycle;

	@Override
	public boolean isEnabledForProvider(@NotNull String providerId) {
		var policy = policyResolver.resolveProviderPolicy(providerId);
		return supportsVerification(providerId) && policy != null && policy.enabled();
	}

	@Override
	public @NotNull VerificationResolutionResult resolveVerification(@NotNull VerificationResolutionRequest request) {
		return verificationRequirementResolver.resolveVerification(request);
	}

	@Override
	public @NotNull VerificationChallengeResult<?> submitChallenge(
			@NotNull String challengeId,
			@NotNull VerificationInteraction interaction
	) {
		return challengeLifecycle.submitChallengeInteraction(challengeId, interaction);
	}

	@Override
	public @NotNull VerificationChallengeResult<?> submitChallenge(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose,
			@NotNull VerificationInteraction interaction
	) {
		return challengeLifecycle.submitChallengeInteraction(uniqueId, providerId, purpose, interaction);
	}

	@Override
	public @NotNull VerificationEnrollmentResult<?> beginEnrollment(
			@NotNull UUID uniqueId,
			@NotNull String username,
			@Nullable String providerId,
			@NotNull String methodId
	) {
		return enrollmentLifecycle.beginEnrollment(uniqueId, username, providerId, methodId);
	}

	@Override
	public @NotNull VerificationEnrollmentResult<?> submitEnrollment(
			@NotNull UUID uniqueId,
			@NotNull VerificationInteraction interaction
	) {
		PendingVerificationEnrollment pendingEnrollment = enrollmentStore.peek(uniqueId).orElse(null);
		VerificationEnrollmentResult<?> result = enrollmentLifecycle.submitEnrollmentInteraction(uniqueId, interaction);
		if (pendingEnrollment != null && result.getStatus() == VerificationEnrollmentStatus.ACTIVATED)
			autoSelectCurrentProvider(uniqueId, pendingEnrollment.getProviderId(), pendingEnrollment.getMethodId(), result);

		return result;
	}

	@Override
	public boolean cancelPendingEnrollment(@NotNull UUID uniqueId) {
		return enrollmentLifecycle.cancelPendingEnrollment(uniqueId);
	}

	@Override
	public @NotNull List<VerificationEnrollment> findEnrollments(@NotNull UUID uniqueId) {
		return persistenceService.findEnrollments(uniqueId);
	}

	@Override
	public @NotNull List<VerificationSelection> findSelections(@NotNull UUID uniqueId) {
		return persistenceService.findSelections(uniqueId);
	}

	@Override
	public @NotNull VerificationSelectionResult selectMethod(
			@NotNull UUID uniqueId,
			@NotNull String providerId,
			@NotNull String methodId
	) {
		VerificationSelectionEvent selectionEvent = new VerificationSelectionEvent(uniqueId, providerId, methodId, false);
		eventManager.call(selectionEvent);
		if (selectionEvent.isCancelled())
			return VerificationSelectionResult.of(
					VerificationSelectionStatus.NOT_ALLOWED,
					selectionEvent.getMethodId(),
					selectionEvent.getProviderId()
			);

		providerId = selectionEvent.getProviderId();
		methodId = selectionEvent.getMethodId();
		if (!policyResolver.hasConfiguredProvider(providerId))
			return VerificationSelectionResult.of(VerificationSelectionStatus.PROVIDER_NOT_FOUND, methodId, providerId);
		if (!supportsVerification(providerId))
			return VerificationSelectionResult.of(VerificationSelectionStatus.PROVIDER_UNSUPPORTED, methodId, providerId);

		VerificationPolicyResolver.ResolvedProviderPolicy providerPolicy = policyResolver.resolveProviderPolicy(providerId);
		if (providerPolicy == null || !providerPolicy.enabled())
			return VerificationSelectionResult.of(
					VerificationSelectionStatus.PROVIDER_VERIFICATION_DISABLED,
					methodId,
					providerId
			);

		VerificationPolicyResolver.ResolvedMethodPolicy policy = policyResolver.resolveMethodPolicy(providerId, methodId);
		if (policy == null || !policy.enabled())
			return VerificationSelectionResult.of(
					VerificationSelectionStatus.METHOD_DISABLED_FOR_PROVIDER,
					methodId,
					providerId
			);
		if (methodRegistry.find(methodId).isEmpty())
			return VerificationSelectionResult.of(
					VerificationSelectionStatus.METHOD_DISABLED_FOR_PROVIDER,
					methodId,
					providerId
			);
		if (persistenceService.findEnrollment(uniqueId, methodId).isEmpty())
			return VerificationSelectionResult.of(
					VerificationSelectionStatus.METHOD_NOT_ENROLLED,
					methodId,
					providerId
			);

		VerificationSelection existing = persistenceService.findSelection(uniqueId, providerId).orElse(null);
		if (existing != null && methodId.equalsIgnoreCase(existing.getMethodId()))
			return VerificationSelectionResult.of(
					VerificationSelectionStatus.ALREADY_SELECTED,
					methodId,
					providerId
			);

		persistenceService.upsertSelection(VerificationSelection.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.methodId(methodId)
				.selectedAt(System.currentTimeMillis())
				.build());
		return VerificationSelectionResult.of(VerificationSelectionStatus.UPDATED, methodId, providerId);
	}

	@Override
	public @NotNull VerificationDisableResult disableMethod(@NotNull UUID uniqueId, @NotNull String methodId) {
		if (persistenceService.findEnrollment(uniqueId, methodId).isEmpty())
			return VerificationDisableResult.builder()
					.status(VerificationDisableStatus.METHOD_NOT_ENROLLED)
					.methodId(methodId)
					.build();

		persistenceService.deleteEnrollment(uniqueId, methodId);
		persistenceService.replaceRecoveryCodes(uniqueId, methodId, List.of());
		for (VerificationSelection selection : persistenceService.findSelections(uniqueId)) {
			if (selection == null || !methodId.equalsIgnoreCase(selection.getMethodId())) continue;

			VerificationPolicyResolver.ResolvedMethodPolicy policy =
					policyResolver.resolveMethodPolicy(selection.getProviderId(), methodId);
			UnavailableSelectionPolicy unavailablePolicy = policy != null
					? policy.unavailableSelectionPolicy()
					: UnavailableSelectionPolicy.CLEAR_SELECTION;
			if (unavailablePolicy == UnavailableSelectionPolicy.CLEAR_SELECTION)
				persistenceService.deleteSelection(uniqueId, selection.getProviderId());
		}
		eventManager.call(new VerificationMethodDisabledEvent(uniqueId, methodId));
		return VerificationDisableResult.builder()
				.status(VerificationDisableStatus.DISABLED)
				.methodId(methodId)
				.build();
	}

	@Override
	public @NotNull VerificationResetResult reset(@NotNull UUID uniqueId, @Nullable String providerId) {
		boolean fullReset = providerId == null || providerId.isBlank();
		if (fullReset) {
			persistenceService.deleteAll(uniqueId);
		} else {
			persistenceService.deleteProviderSelections(uniqueId, providerId);
		}

		enrollmentStore.clear(uniqueId);
		eventManager.call(new VerificationResetEvent(uniqueId, providerId, fullReset));
		return VerificationResetResult.builder()
				.status(VerificationResetStatus.RESET)
				.providerId(providerId)
				.build();
	}

	private void autoSelectCurrentProvider(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@NotNull String methodId,
			@NotNull VerificationEnrollmentResult<?> result
	) {
		if (!verificationProvider.get().isAutoSelectCurrentProvider()) return;

		String resolvedProviderId = providerId;
		if (resolvedProviderId == null || resolvedProviderId.isBlank()) {
			Session session = sessionService.findByUniqueId(uniqueId).join().orElse(null);
			resolvedProviderId = session != null ? session.getProviderId() : null;
		}
		if (resolvedProviderId == null || resolvedProviderId.isBlank()) return;
		if (persistenceService.findSelection(uniqueId, resolvedProviderId).isPresent()) return;

		VerificationSelectionResult selection = selectMethod(uniqueId, resolvedProviderId, methodId);
		if (selection.getStatus() == VerificationSelectionStatus.UPDATED)
			result.setAutoSelectedProviderId(resolvedProviderId);
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
