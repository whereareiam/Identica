package me.whereareiam.identica.feature.verification.challenge;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.verification.VerificationInteraction;
import me.whereareiam.identica.feature.verification.VerificationMethod;
import me.whereareiam.identica.feature.verification.VerificationRegistry;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.event.challenge.VerificationChallengeFailedEvent;
import me.whereareiam.identica.feature.verification.event.challenge.VerificationChallengeStartedEvent;
import me.whereareiam.identica.feature.verification.event.challenge.VerificationChallengeSucceededEvent;
import me.whereareiam.identica.feature.verification.model.challenge.PendingVerificationChallenge;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeContext;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.process.VerificationChallengeProcess;
import me.whereareiam.identica.feature.verification.state.VerificationChallengeState;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VerificationChallengeLifecycle {
	private final VerificationPersistenceService persistenceService;
	private final VerificationChallengeStore challengeStore;
	private final VerificationStateCodec stateCodec;
	private final VerificationRegistry methodRegistry;
	private final Provider<VerificationSettings> verificationProvider;
	private final EventManager eventManager;

	public boolean hasActiveChallenge(@NotNull UUID uniqueId, @Nullable String providerId, @Nullable String purpose) {
		return challengeStore.findActive(uniqueId, providerId, purpose).isPresent();
	}

	public @NotNull VerificationChallengeResult<?> submitChallengeInteraction(
			@NotNull String challengeId,
			@NotNull VerificationInteraction interaction
	) {
		PendingVerificationChallenge record = challengeStore.find(challengeId).orElse(null);
		if (record == null)
			return VerificationChallengeResult.of(
					VerificationChallengeStatus.EXPIRED,
					challengeId,
					null,
				null,
				false
			);

		return submit(record, interaction);
	}

	public @NotNull VerificationChallengeResult<?> submitChallengeInteraction(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose,
			@NotNull VerificationInteraction interaction
	) {
		PendingVerificationChallenge record = challengeStore.findActive(uniqueId, providerId, purpose).orElse(null);
		if (record == null)
			return VerificationChallengeResult.of(
						VerificationChallengeStatus.METHOD_NOT_SELECTED,
						null,
						null,
					providerId,
					false
			);

		return submit(record, interaction);
	}

	public @NotNull VerificationChallengeResult<?> startChallenge(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose,
			@NotNull List<VerificationEnrollment> enrollments,
			@NotNull VerificationMethod method,
			boolean required
	) {
		VerificationChallengeProcess<?> process = method.challenge();
		return startChallengeTyped(uniqueId, providerId, purpose, enrollments, method, required, process);
	}

	private <S extends VerificationChallengeState> @NotNull VerificationChallengeResult<S> startChallengeTyped(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose,
			@NotNull List<VerificationEnrollment> enrollments,
			@NotNull VerificationMethod method,
			boolean required,
			@NotNull VerificationChallengeProcess<S> process
	) {
		String challengeId = UUID.randomUUID().toString();
		VerificationChallengeContext<S> context = VerificationChallengeContext.<S>builder()
				.challengeId(challengeId)
				.subjectUniqueId(uniqueId)
				.methodId(method.descriptor().getId())
				.enrollments(enrollments)
				.providerId(providerId)
				.purpose(purpose)
				.required(required)
				.build();

		VerificationChallengeResult<S> result = process.start(context);
		S state = result.getState();
		if (state != null) {
			challengeStore.put(PendingVerificationChallenge.builder()
					.challengeId(challengeId)
					.uniqueId(uniqueId)
					.methodId(method.descriptor().getId())
					.providerId(providerId)
					.purpose(purpose)
					.required(required)
					.stateType(state.getClass().getName())
					.statePayload(stateCodec.encode(state))
					.createdAt(System.currentTimeMillis())
					.expiresAt(System.currentTimeMillis() + verificationProvider.get().challengeTtlMillis())
					.build());
		}
		result.setChallengeId(challengeId);
		result.setMethodId(method.descriptor().getId());
		result.setProviderId(providerId);
		result.setRequired(required);
		eventManager.call(new VerificationChallengeStartedEvent(uniqueId, providerId, method.descriptor().getId(), challengeId));
		return result;
	}

	private @NotNull VerificationChallengeResult<?> submit(
			@NotNull PendingVerificationChallenge record,
			@NotNull VerificationInteraction interaction
	) {
		VerificationMethod method = methodRegistry.find(record.getMethodId()).orElse(null);
		if (method == null)
			return VerificationChallengeResult.of(
						VerificationChallengeStatus.METHOD_UNAVAILABLE,
						record.getChallengeId(),
						record.getMethodId(),
					record.getProviderId(),
					record.isRequired()
			);

		VerificationChallengeResult<?> result = submitTyped(record, method.challenge(), interaction);
		if (result.getStatus() == VerificationChallengeStatus.VERIFIED) {
			challengeStore.markVerified(record);
			eventManager.call(new VerificationChallengeSucceededEvent(
					record.getUniqueId(),
					record.getProviderId(),
					record.getMethodId(),
					result.isRecoveryCodeUsed()
			));
			return result;
		}

		if (result.getStatus() == VerificationChallengeStatus.INVALID)
			eventManager.call(new VerificationChallengeFailedEvent(
					record.getUniqueId(),
					record.getProviderId(),
					record.getMethodId(),
					result.getStatus()
			));

		if (result.getState() != null) {
			VerificationChallengeState state = result.getState();
			record.setStateType(state.getClass().getName());
			record.setStatePayload(stateCodec.encode(state));
			challengeStore.put(record);
		}
		return result;
	}

	private <S extends VerificationChallengeState> @NotNull VerificationChallengeResult<S> submitTyped(
			@NotNull PendingVerificationChallenge record,
			@NotNull VerificationChallengeProcess<S> process,
			@NotNull VerificationInteraction interaction
	) {
		S state = stateCodec.decode(record.getStatePayload(), process.stateType());
		List<VerificationEnrollment> enrollments = persistenceService.findEnrollments(record.getUniqueId()).stream()
				.filter(enrollment -> enrollment != null && record.getMethodId().equalsIgnoreCase(enrollment.getMethodId()))
				.toList();

		if (state == null || enrollments.isEmpty())
			return VerificationChallengeResult.of(
						VerificationChallengeStatus.METHOD_UNAVAILABLE,
						record.getChallengeId(),
						record.getMethodId(),
					record.getProviderId(),
					record.isRequired()
			);

		VerificationChallengeContext<S> context = VerificationChallengeContext.<S>builder()
				.challengeId(record.getChallengeId())
				.subjectUniqueId(record.getUniqueId())
				.methodId(record.getMethodId())
				.enrollments(enrollments)
				.providerId(record.getProviderId())
				.purpose(record.getPurpose())
				.required(record.isRequired())
				.state(state)
				.build();
		return process.submit(context, state, interaction);
	}
}
