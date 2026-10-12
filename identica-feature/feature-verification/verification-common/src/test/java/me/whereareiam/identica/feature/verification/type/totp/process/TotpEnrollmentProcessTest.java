package me.whereareiam.identica.feature.verification.type.totp.process;

import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentContext;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentResult;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.interaction.SavedVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessContext;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessResult;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessTransition;
import me.whereareiam.identica.feature.verification.type.totp.TotpClock;
import me.whereareiam.identica.feature.verification.type.totp.TotpCodec;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpEnrollmentState;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmCodeStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmSavedStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpSetupStep;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.InstantSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TotpEnrollmentProcessTest {
	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
	private static final TotpClock CLOCK = new TotpClock(InstantSource.fixed(NOW));

	@Test
	void startStoresNextCursorAfterSetupStep() {
		VerificationSettings verification = verification();
		TotpEnrollmentProcess process = process(verification);

		VerificationEnrollmentResult<TotpEnrollmentState> result = process.start(context(UUID.randomUUID(), null));

		assertEquals("totp-confirm-code", result.getState().getStepId());
		assertNotNull(result.getDisplay());
		assertFalse(result.getDisplay().getLines().isEmpty());
		assertTrue(result.getDisplay().getLines().stream().anyMatch(line -> line.contains("TOTP secret")));
		assertNotNull(result.getDisplay().getPlaceholders().get("secret"));
		assertNotNull(result.getDisplay().getPlaceholders().get("uri"));
	}

	@Test
	void invalidCodeStaysOnConfirmCodeStep() {
		VerificationSettings verification = verification();
		TotpEnrollmentProcess process = process(verification);
		UUID subjectUniqueId = UUID.randomUUID();
		VerificationEnrollmentResult<TotpEnrollmentState> started = process.start(context(subjectUniqueId, null));
		TotpEnrollmentState state = started.getState();

		VerificationEnrollmentResult<TotpEnrollmentState> result = process.submit(
				context(subjectUniqueId, state),
				state,
				CodeVerificationInteraction.builder()
						.subjectUniqueId(subjectUniqueId)
						.code("bad")
						.build()
		);

		assertEquals("totp-confirm-code", result.getState().getStepId());
		assertEquals(state.getSecret(), result.getState().getSecret());
		assertTrue(result.getState().getRecoveryCodes().isEmpty());
		assertFalse(result.getState().isVerified());
	}

	@Test
	void validCodeAdvancesToSavedConfirmation() {
		VerificationSettings verification = verification();
		TotpEnrollmentProcess process = process(verification);
		UUID subjectUniqueId = UUID.randomUUID();
		VerificationEnrollmentResult<TotpEnrollmentState> started = process.start(context(subjectUniqueId, null));
		TotpEnrollmentState state = started.getState();
		String code = TotpCodec.code(
				state.getSecret(),
				TotpCodec.timeStep(NOW, verification.getTotp().periodSeconds()),
				verification.getTotp().getDigits()
		);

		VerificationEnrollmentResult<TotpEnrollmentState> result = process.submit(
				context(subjectUniqueId, state),
				state,
				CodeVerificationInteraction.builder()
						.subjectUniqueId(subjectUniqueId)
						.code(code)
						.build()
		);

		assertEquals("totp-confirm-saved", result.getState().getStepId());
		assertTrue(result.getState().isVerified());
		assertFalse(result.getState().getRecoveryCodes().isEmpty());
		assertEquals(TotpCodec.timeStep(NOW, verification.getTotp().periodSeconds()), result.getState().acceptedCounter());
	}

	@Test
	void savedConfirmationCompletesWithoutChangingCursor() {
		VerificationSettings verification = verification();
		TotpEnrollmentProcess process = process(verification);
		UUID subjectUniqueId = UUID.randomUUID();
		VerificationEnrollmentResult<TotpEnrollmentState> started = process.start(context(subjectUniqueId, null));
		TotpEnrollmentState confirmedCodeState = confirmCode(process, verification, subjectUniqueId, started.getState());

		VerificationEnrollmentResult<TotpEnrollmentState> result = process.submit(
				context(subjectUniqueId, confirmedCodeState),
				confirmedCodeState,
				SavedVerificationInteraction.builder()
						.subjectUniqueId(subjectUniqueId)
						.build()
		);

		assertEquals("totp-confirm-saved", result.getState().getStepId());
		assertTrue(result.getState().isVerified());
		assertFalse(result.getState().getRecoveryCodes().isEmpty());
		assertEquals(TotpCodec.timeStep(NOW, verification.getTotp().periodSeconds()), result.getState().acceptedCounter());
	}

	@Test
	void gotoTransitionJumpsToExplicitStep() {
		VerificationSettings verification = verification();
		TotpSetupStep setupStep = new TotpSetupStep(() -> verification, this::messages);
		TotpConfirmSavedStep confirmSavedStep = new TotpConfirmSavedStep();
		TotpEnrollmentProcess process = new TotpEnrollmentProcess(
				setupStep,
				new RedirectingConfirmCodeStep(verification, confirmSavedStep.id()),
				confirmSavedStep
		);
		UUID subjectUniqueId = UUID.randomUUID();
		VerificationEnrollmentResult<TotpEnrollmentState> started = process.start(context(subjectUniqueId, null));
		TotpEnrollmentState state = started.getState();
		String startedStepId = state.getStepId();

		VerificationEnrollmentResult<TotpEnrollmentState> result = process.submit(
				context(subjectUniqueId, state),
				state,
				CodeVerificationInteraction.builder()
						.subjectUniqueId(subjectUniqueId)
						.code("ignored")
						.build()
		);

		assertNotEquals(startedStepId, result.getState().getStepId());
		assertEquals("totp-confirm-saved", result.getState().getStepId());
	}

	private @NotNull TotpEnrollmentState confirmCode(
			@NotNull TotpEnrollmentProcess process,
			@NotNull VerificationSettings verification,
			@NotNull UUID subjectUniqueId,
			@NotNull TotpEnrollmentState state
	) {
		String code = TotpCodec.code(
				state.getSecret(),
				TotpCodec.timeStep(NOW, verification.getTotp().periodSeconds()),
				verification.getTotp().getDigits()
		);
		return process.submit(
				context(subjectUniqueId, state),
				state,
				CodeVerificationInteraction.builder()
						.subjectUniqueId(subjectUniqueId)
						.code(code)
						.build()
		).getState();
	}

	private @NotNull TotpEnrollmentProcess process(@NotNull VerificationSettings verification) {
		return new TotpEnrollmentProcess(
				new TotpSetupStep(() -> verification, this::messages),
				new TotpConfirmCodeStep(() -> verification, CLOCK),
				new TotpConfirmSavedStep()
		);
	}

	private @NotNull VerificationEnrollmentContext<TotpEnrollmentState> context(
			@NotNull UUID subjectUniqueId,
			TotpEnrollmentState state
	) {
		return VerificationEnrollmentContext.<TotpEnrollmentState>builder()
				.enrollmentId("enrollment")
				.subjectUniqueId(subjectUniqueId)
				.username("player")
				.methodId("totp")
				.providerId("auth")
				.state(state)
				.build();
	}

	private @NotNull VerificationSettings verification() {
		return new VerificationDefaults().supply(new VerificationSettings());
	}

	private @NotNull VerificationMessages messages() {
		VerificationMessages messages = new VerificationMessages();
		VerificationMessages.Methods.Totp totp = new VerificationMessages.Methods.Totp();
		totp.setPending(java.util.List.of("TOTP secret {secret}"));
		VerificationMessages.Methods methods = new VerificationMessages.Methods();
		methods.setTotp(totp);
		messages.setMethods(methods);
		return messages;
	}

	private static final class RedirectingConfirmCodeStep extends TotpConfirmCodeStep {
		private final @NotNull String targetStepId;

		private RedirectingConfirmCodeStep(@NotNull VerificationSettings verification, @NotNull String targetStepId) {
			super(() -> verification, CLOCK);
			this.targetStepId = targetStepId;
		}

		@Override
		public @NotNull VerificationProcessResult<TotpEnrollmentState> submit(
				@NotNull VerificationProcessContext<TotpEnrollmentState> context,
				@NotNull CodeVerificationInteraction interaction
		) {
			return VerificationProcessResult.waiting(
					context.getState(),
					VerificationProcessTransition.goTo(targetStepId)
			);
		}
	}
}
