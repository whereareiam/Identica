package me.whereareiam.identica.feature.verification.type.totp.step;

import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessContext;
import me.whereareiam.identica.feature.verification.type.process.VerificationProcessStatus;
import me.whereareiam.identica.feature.verification.type.totp.TotpClock;
import me.whereareiam.identica.feature.verification.type.totp.TotpCodec;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpChallengeState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpChallengeVerificationStepTest {
	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
	private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
	private static final UUID ALICE = UUID.fromString("6f1c2a52-8e1b-4a8e-9a43-3b1f7f0c2d11");

	private final VerificationSettings verification = new VerificationDefaults().supply(new VerificationSettings());
	private final VerificationPersistenceService persistence = mock(VerificationPersistenceService.class);
	private final TotpChallengeVerificationStep step = new TotpChallengeVerificationStep(
			() -> verification,
			persistence,
			new TotpClock(InstantSource.fixed(NOW))
	);
	private final long now = TotpCodec.timeStep(NOW, verification.getTotp().periodSeconds());

	@Test
	void acceptsACodeByRaisingTheEnrollmentsAcceptedCounterToItsStep() {
		when(persistence.acceptCounter(ALICE, "totp", "default", now)).thenReturn(true);

		assertEquals(VerificationProcessStatus.VERIFIED, submit(code(now)));
		verify(persistence).acceptCounter(ALICE, "totp", "default", now);
	}

	@Test
	void acceptsTheNewestStepACodeMatchesAheadOfTheClock() {
		when(persistence.acceptCounter(ALICE, "totp", "default", now + 1)).thenReturn(true);

		assertEquals(VerificationProcessStatus.VERIFIED, submit(code(now + 1)));
	}

	@Test
	void refusesACodeWhoseStepWasAcceptedAlreadyLikeAWrongCode() {
		when(persistence.acceptCounter(ALICE, "totp", "default", now)).thenReturn(false);

		assertEquals(VerificationProcessStatus.INVALID, submit(code(now)));
		verify(persistence).markRecoveryCodeUsed(eq(ALICE), eq("totp"), anyString(), anyLong());
	}

	@Test
	void doesNotTouchTheCounterForACodeOutsideTheDriftWindow() {
		assertEquals(VerificationProcessStatus.INVALID, submit(code(now - 2)));
		verify(persistence, never()).acceptCounter(ALICE, "totp", "default", now - 2);
	}

	private String code(long step) {
		return TotpCodec.code(SECRET, step, verification.getTotp().getDigits());
	}

	private VerificationProcessStatus submit(String code) {
		VerificationEnrollment enrollment = VerificationEnrollment.builder()
				.uniqueId(ALICE)
				.methodId("totp")
				.enrollmentId("default")
				.credential(SECRET)
				.build();
		VerificationProcessContext<TotpChallengeState> context = VerificationProcessContext.<TotpChallengeState>builder()
				.subjectUniqueId(ALICE)
				.methodId("totp")
				.enrollments(List.of(enrollment))
				.build();

		return step.submit(context, CodeVerificationInteraction.builder().subjectUniqueId(ALICE).code(code).build()).getStatus();
	}
}
