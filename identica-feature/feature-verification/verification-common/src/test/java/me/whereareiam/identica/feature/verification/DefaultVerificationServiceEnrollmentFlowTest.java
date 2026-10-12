package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeLifecycle;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.challenge.VerificationStateCodec;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentActivator;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentLifecycle;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentStore;
import me.whereareiam.identica.feature.verification.model.VerificationRecoveryCode;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.PendingVerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentResult;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.interaction.SavedVerificationInteraction;
import me.whereareiam.identica.feature.verification.resolution.VerificationRequirementResolver;
import me.whereareiam.identica.feature.verification.type.totp.TotpClock;
import me.whereareiam.identica.feature.verification.type.totp.TotpCodec;
import me.whereareiam.identica.feature.verification.type.totp.TotpVerificationMethod;
import me.whereareiam.identica.feature.verification.type.totp.process.TotpChallengeProcess;
import me.whereareiam.identica.feature.verification.type.totp.process.TotpEnrollmentProcess;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpEnrollmentState;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpChallengeVerificationStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmCodeStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmSavedStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpSetupStep;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.provider.ProviderManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultVerificationServiceEnrollmentFlowTest {
	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
	private static final TotpClock CLOCK = new TotpClock(InstantSource.fixed(NOW));

	private final VerificationMessages commandsMessages = messages();

	@Test
	@SuppressWarnings("unchecked")
	void beginEnrollmentPersistsStateAndResumesUntilActivation() {
		VerificationSettings verification = new VerificationDefaults().supply(new VerificationSettings());
		verification.setAutoSelectCurrentProvider(false);
		VerificationPersistenceService persistenceService = mock(VerificationPersistenceService.class);
		VerificationEnrollmentStore enrollmentStore = mock(VerificationEnrollmentStore.class);
		VerificationChallengeStore challengeStore = mock(VerificationChallengeStore.class);
		VerificationStateCodec stateCodec = new VerificationStateCodec();
			VerificationPolicyResolver policyResolver = mock(VerificationPolicyResolver.class);
			ProviderManager providerManager = mock(ProviderManager.class);
			SessionService sessionService = mock(SessionService.class);
			EventManager eventManager = mock(EventManager.class);
			UUID uniqueId = UUID.randomUUID();
			AtomicReference<PendingVerificationEnrollment> pending = new AtomicReference<>();
			TotpVerificationMethod method = method(verification, persistenceService);
			DefaultVerificationRegistry registry = new DefaultVerificationRegistry(Set.of(method));
			VerificationChallengeLifecycle challengeLifecycle = new VerificationChallengeLifecycle(
					persistenceService,
					challengeStore,
					stateCodec,
					registry,
					() -> verification,
					eventManager
			);
			VerificationEnrollmentActivator enrollmentActivator = new VerificationEnrollmentActivator(
					persistenceService,
					eventManager
			);
			VerificationEnrollmentLifecycle enrollmentLifecycle = new VerificationEnrollmentLifecycle(
					persistenceService,
					enrollmentStore,
					stateCodec,
					registry,
					() -> verification,
					eventManager,
					enrollmentActivator
			);
			VerificationRequirementResolver verificationRequirementResolver = new VerificationRequirementResolver(
					persistenceService,
					challengeStore,
					policyResolver,
					registry,
					providerManager,
					challengeLifecycle
			);
			DefaultVerificationService service = new DefaultVerificationService(
					persistenceService,
					enrollmentStore,
					() -> verification,
					registry,
					providerManager,
					policyResolver,
					sessionService,
					eventManager,
					verificationRequirementResolver,
					challengeLifecycle,
					enrollmentLifecycle
			);

		when(persistenceService.findEnrollment(uniqueId, "totp")).thenReturn(Optional.empty());
		doAnswer(invocation -> {
			pending.set(invocation.getArgument(1));
			return null;
		}).when(enrollmentStore).put(eq(uniqueId), any(PendingVerificationEnrollment.class));
		when(enrollmentStore.peek(uniqueId)).thenAnswer(invocation -> Optional.ofNullable(pending.get()));
		when(enrollmentStore.clear(uniqueId)).thenAnswer(invocation -> pending.getAndSet(null) != null);

		VerificationEnrollmentResult<?> started = service.beginEnrollment(uniqueId, "player", "auth", "totp");
		TotpEnrollmentState startedState = (TotpEnrollmentState) started.getState();
		assertNotNull(startedState);
		assertEquals("totp-confirm-code", startedState.getStepId());

		PendingVerificationEnrollment storedAfterStart = pending.get();
		assertNotNull(storedAfterStart);
		TotpEnrollmentState resumedAfterStart = stateCodec.decode(storedAfterStart.getStatePayload(), TotpEnrollmentState.class);
		assertNotNull(resumedAfterStart);
		assertEquals("totp-confirm-code", resumedAfterStart.getStepId());

		long step = TotpCodec.timeStep(NOW, verification.getTotp().periodSeconds());
		String code = TotpCodec.code(startedState.getSecret(), step, verification.getTotp().getDigits());
		VerificationEnrollmentResult<?> awaitingSavedConfirmation = service.submitEnrollment(
				uniqueId,
				CodeVerificationInteraction.builder()
						.subjectUniqueId(uniqueId)
						.code(code)
						.build()
		);

		TotpEnrollmentState waitingState = (TotpEnrollmentState) awaitingSavedConfirmation.getState();
		assertNotNull(waitingState);
		assertEquals("totp-confirm-saved", waitingState.getStepId());
		PendingVerificationEnrollment storedAfterCode = pending.get();
		assertNotNull(storedAfterCode);
		TotpEnrollmentState resumedAfterCode = stateCodec.decode(storedAfterCode.getStatePayload(), TotpEnrollmentState.class);
		assertNotNull(resumedAfterCode);
		assertEquals("totp-confirm-saved", resumedAfterCode.getStepId());

		VerificationEnrollmentResult<?> activated = service.submitEnrollment(
				uniqueId,
				SavedVerificationInteraction.builder()
						.subjectUniqueId(uniqueId)
						.build()
		);

		assertEquals("totp-confirm-saved", ((TotpEnrollmentState) activated.getState()).getStepId());
		assertNull(pending.get());

		ArgumentCaptor<VerificationEnrollment> enrollmentCaptor = ArgumentCaptor.forClass(VerificationEnrollment.class);
		verify(persistenceService).upsertEnrollment(enrollmentCaptor.capture());
		assertEquals(step, enrollmentCaptor.getValue().getAcceptedCounter(), "the confirming code's step is accepted already");
		verify(persistenceService, never()).acceptCounter(any(), any(), any(), anyLong());
		ArgumentCaptor<List<VerificationRecoveryCode>> recoveryCodesCaptor = ArgumentCaptor.forClass(List.class);
		verify(persistenceService).replaceRecoveryCodes(eq(uniqueId), eq("totp"), recoveryCodesCaptor.capture());
        assertFalse(recoveryCodesCaptor.getValue().isEmpty());
		verify(enrollmentStore).clear(uniqueId);
		verify(sessionService, never()).findByUniqueId(any());
		verify(eventManager, atLeastOnce()).call(any());
	}

	private @NotNull TotpVerificationMethod method(
			@NotNull VerificationSettings verification,
			@NotNull VerificationPersistenceService persistenceService
	) {
		TotpSetupStep setupStep = new TotpSetupStep(() -> verification, () -> commandsMessages);
		TotpConfirmCodeStep confirmCodeStep = new TotpConfirmCodeStep(() -> verification, CLOCK);
		TotpConfirmSavedStep confirmSavedStep = new TotpConfirmSavedStep();
		TotpChallengeVerificationStep challengeStep = new TotpChallengeVerificationStep(() -> verification, persistenceService, CLOCK);
		return new TotpVerificationMethod(
				() -> verification,
				new TotpEnrollmentProcess(setupStep, confirmCodeStep, confirmSavedStep),
				new TotpChallengeProcess(challengeStep)
		);
	}

	private VerificationMessages messages() {
		VerificationMessages messages = new VerificationMessages();
		VerificationMessages.Methods.Totp totp = new VerificationMessages.Methods.Totp();
		totp.setPending(List.of("TOTP secret {secret}"));
		VerificationMessages.Methods methods = new VerificationMessages.Methods();
		methods.setTotp(totp);
		messages.setMethods(methods);
		return messages;
	}
}
