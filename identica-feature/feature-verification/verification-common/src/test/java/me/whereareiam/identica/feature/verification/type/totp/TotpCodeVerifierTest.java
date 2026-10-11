package me.whereareiam.identica.feature.verification.type.totp;

import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessContext;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessResult;
import me.whereareiam.identica.feature.verification.type.process.VerificationProcessStatus;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpChallengeState;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpEnrollmentState;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpChallengeVerificationStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmCodeStep;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotpCodeVerifierTest {
	private final UUID uniqueId = UUID.randomUUID();
	private final String secret = TotpCodec.generateSecret(20);
	private final Map<String, Long> lastSteps = new ConcurrentHashMap<>();
	private VerificationSettings verification;
	private VerificationPersistenceService persistenceService;
	private TotpCodeVerifier verifier;
	private long now;

	@BeforeEach
	void setUp() {
		verification = new VerificationDefaults().supply(new VerificationSettings());
		// A long period keeps every code of a test in the same time step.
		verification.getTotp().setPeriod(Duration.ofHours(1));
		persistenceService = mock(VerificationPersistenceService.class);
		when(persistenceService.acceptTimeStep(any(), anyString(), anyLong())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0) + ":" + invocation.getArgument(1);
			long step = invocation.getArgument(2);
			AtomicBoolean accepted = new AtomicBoolean();
			lastSteps.compute(key, (ignored, last) -> {
				if (last != null && last >= step) return last;
				accepted.set(true);
				return step;
			});
			return accepted.get();
		});
		verifier = new TotpCodeVerifier(() -> verification, persistenceService);
		now = TotpCodec.currentStep(verification.getTotp().periodSeconds());
	}

	@Test
	void refusesACodeAcceptedBeforeWithinItsWindow() {
		String code = code(now);

		assertTrue(verifier.accept(uniqueId, "totp", secret, code));
		assertFalse(verifier.accept(uniqueId, "totp", secret, code));
	}

	@Test
	void acceptsTheCodeOfTheNextTimeStep() {
		assertTrue(verifier.accept(uniqueId, "totp", secret, code(now)));

		assertTrue(verifier.accept(uniqueId, "totp", secret, code(now + 1)));
	}

	@Test
	void acceptsCodesWithinTheAllowedDriftButNeverAnOlderStepThanTheLastAccepted() {
		assertTrue(verifier.accept(uniqueId, "totp", secret, code(now - 1)));
		assertTrue(verifier.accept(uniqueId, "totp", secret, code(now + 1)));

		assertFalse(verifier.accept(uniqueId, "totp", secret, code(now)));
		assertFalse(verifier.accept(uniqueId, "totp", secret, code(now - 1)));
	}

	@Test
	void refusesCodesOutsideTheAllowedDrift() {
		assertFalse(verifier.accept(uniqueId, "totp", secret, code(now - 2)));
		assertFalse(verifier.accept(uniqueId, "totp", secret, code(now + 2)));
	}

	@Test
	void keepsTheLastAcceptedStepPerAccountAndMethod() {
		String code = code(now);

		assertTrue(verifier.accept(uniqueId, "totp", secret, code));
		assertTrue(verifier.accept(uniqueId, "other", secret, code));
		assertTrue(verifier.accept(UUID.randomUUID(), "totp", secret, code));
	}

	@Test
	void acceptsTheSameCodeSubmittedConcurrentlyOnlyOnce() throws Exception {
		String code = code(now);
		int submissions = 16;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(submissions);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < submissions; i++)
				results.add(executor.submit(() -> {
					start.await();
					return verifier.accept(uniqueId, "totp", secret, code);
				}));
			start.countDown();

			int accepted = 0;
			for (Future<Boolean> result : results)
				if (result.get()) accepted++;
			assertEquals(1, accepted);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void refusesAUsedCodeAtALoginChallengeLikeAWrongCode() {
		TotpChallengeVerificationStep step = new TotpChallengeVerificationStep(verifier, persistenceService);
		String code = code(now);

		VerificationProcessResult<TotpChallengeState> first = step.submit(challengeContext(null), interaction(code));
		VerificationProcessResult<TotpChallengeState> second = step.submit(challengeContext(null), interaction(code));
		VerificationProcessResult<TotpChallengeState> wrong = step.submit(challengeContext(null), interaction(wrongCode()));

		assertEquals(VerificationProcessStatus.VERIFIED, first.getStatus());
		assertEquals(VerificationProcessStatus.INVALID, second.getStatus());
		assertEquals(wrong.getStatus(), second.getStatus());
		assertEquals(wrong.getState().getAttempts(), second.getState().getAttempts());
	}

	@Test
	void refusesTheCodeThatConfirmedTheEnrollmentAtTheNextChallenge() {
		TotpConfirmCodeStep confirmStep = new TotpConfirmCodeStep(() -> verification, verifier);
		TotpChallengeVerificationStep challengeStep = new TotpChallengeVerificationStep(verifier, persistenceService);
		String code = code(now);
		TotpEnrollmentState enrollment = TotpEnrollmentState.builder()
				.stepId(confirmStep.id())
				.secret(secret)
				.uri("otpauth://totp/test")
				.build();

		VerificationProcessResult<TotpEnrollmentState> confirmed = confirmStep.submit(
				VerificationProcessContext.<TotpEnrollmentState>builder()
						.subjectUniqueId(uniqueId)
						.methodId("totp")
						.state(enrollment)
						.build(),
				interaction(code)
		);

		assertTrue(confirmed.getState().isVerified());
		assertEquals(VerificationProcessStatus.INVALID, challengeStep.submit(challengeContext(null), interaction(code)).getStatus());
		assertEquals(VerificationProcessStatus.VERIFIED, challengeStep.submit(challengeContext(null), interaction(code(now + 1))).getStatus());
	}

	private @NotNull VerificationProcessContext<TotpChallengeState> challengeContext(TotpChallengeState state) {
		return VerificationProcessContext.<TotpChallengeState>builder()
				.subjectUniqueId(uniqueId)
				.methodId("totp")
				.providerId("credential")
				.enrollments(List.of(VerificationEnrollment.builder()
						.uniqueId(uniqueId)
						.methodId("totp")
						.credential(secret)
						.build()))
				.state(state)
				.build();
	}

	private @NotNull CodeVerificationInteraction interaction(@NotNull String code) {
		return CodeVerificationInteraction.builder()
				.subjectUniqueId(uniqueId)
				.code(code)
				.build();
	}

	private @NotNull String code(long step) {
		return TotpCodec.code(secret, verification.getTotp().getDigits(), step);
	}

	private @NotNull String wrongCode() {
		List<String> nearby = List.of(code(now - 1), code(now), code(now + 1));
		int candidate = 0;
		String wrong;
		do {
			wrong = "%06d".formatted(candidate++);
		} while (nearby.contains(wrong));
		return wrong;
	}
}
