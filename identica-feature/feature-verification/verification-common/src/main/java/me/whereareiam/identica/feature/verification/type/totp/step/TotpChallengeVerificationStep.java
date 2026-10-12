package me.whereareiam.identica.feature.verification.type.totp.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.verification.VerificationInteraction;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.interaction.RecoveryCodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessContext;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessResult;
import me.whereareiam.identica.feature.verification.process.VerificationProcessStep;
import me.whereareiam.identica.feature.verification.type.process.VerificationProcessStatus;
import me.whereareiam.identica.feature.verification.type.totp.RecoveryCodeGenerator;
import me.whereareiam.identica.feature.verification.type.totp.TotpClock;
import me.whereareiam.identica.feature.verification.type.totp.TotpCodec;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpChallengeState;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TotpChallengeVerificationStep implements VerificationProcessStep<VerificationInteraction, TotpChallengeState> {
	private final Provider<VerificationSettings> verificationProvider;
	private final VerificationPersistenceService persistenceService;
	private final TotpClock clock;

	@Override
	public @NotNull String id() {
		return "totp-verify-challenge";
	}

	@Override
	public @NotNull Class<VerificationInteraction> interactionType() {
		return VerificationInteraction.class;
	}

	@Override
	public @NotNull Class<TotpChallengeState> stateType() {
		return TotpChallengeState.class;
	}

	@Override
	public @NotNull VerificationProcessResult<TotpChallengeState> start(
			@NotNull VerificationProcessContext<TotpChallengeState> context
	) {
		TotpChallengeState state = context.getState();
		if (state == null) {
			state = TotpChallengeState.builder()
					.stepId(id())
					.attempts(0)
					.build();
		}
		return VerificationProcessResult.waiting(state);
	}

	@Override
	public @NotNull VerificationProcessResult<TotpChallengeState> submit(
			@NotNull VerificationProcessContext<TotpChallengeState> context,
			@NotNull VerificationInteraction interaction
	) {
		TotpChallengeState state = context.getState();
		if (state == null)
			state = TotpChallengeState.builder().stepId(id()).attempts(0).build();

		String code = null;
		if (interaction instanceof CodeVerificationInteraction codeInteraction)
			code = codeInteraction.getCode();
		if (interaction instanceof RecoveryCodeVerificationInteraction recoveryInteraction)
			code = recoveryInteraction.getCode();

		if (code == null || code.isBlank()) {
			Logger.debug(
					"TOTP challenge rejected blank input uniqueId=%s provider=%s purpose=%s attempts=%s",
					context.getSubjectUniqueId(),
					context.getProviderId(),
					context.getPurpose(),
					state.getAttempts()
			);
			return VerificationProcessResult.waiting(state);
		}

		VerificationEnrollment enrollment = context.getEnrollments().isEmpty() ? null : context.getEnrollments().getFirst();
		if (enrollment != null && accept(context, enrollment, code)) {
			Logger.debug(
					"TOTP challenge verified uniqueId=%s provider=%s purpose=%s attempts=%s",
					context.getSubjectUniqueId(),
					context.getProviderId(),
					context.getPurpose(),
					state.getAttempts()
			);
			return VerificationProcessResult.verified(state);
		}

		String codeHash = RecoveryCodeGenerator.hash(code);
		boolean recoveryCodeUsed = persistenceService.markRecoveryCodeUsed(
				context.getSubjectUniqueId(),
				context.getMethodId(),
				codeHash,
				System.currentTimeMillis()
		);
		if (recoveryCodeUsed)
			state.setRecoveryCodeUsed(true);
		if (recoveryCodeUsed) {
			Logger.debug(
					"TOTP challenge verified with recovery code uniqueId=%s provider=%s purpose=%s attempts=%s",
					context.getSubjectUniqueId(),
					context.getProviderId(),
					context.getPurpose(),
					state.getAttempts()
			);
			return VerificationProcessResult.verified(state);
		}

		state.setAttempts(state.getAttempts() + 1);
		Logger.debug(
				"TOTP challenge invalid code uniqueId=%s provider=%s purpose=%s attempts=%s codeLength=%s",
				context.getSubjectUniqueId(),
				context.getProviderId(),
				context.getPurpose(),
				state.getAttempts(),
				code.length()
		);
		return VerificationProcessResult.<TotpChallengeState>builder()
				.status(VerificationProcessStatus.INVALID)
				.state(state)
				.build();
	}

	/**
	 * Accepts a code of the enrollment's secret once: its time step has to be newer than the one
	 * accepted last, which it then becomes, on every proxy that shares the database.
	 */
	private boolean accept(
			@NotNull VerificationProcessContext<TotpChallengeState> context,
			@NotNull VerificationEnrollment enrollment,
			@NotNull String code
	) {
		String secret = enrollment.getCredential();
		if (secret == null || secret.isBlank()) return false;

		VerificationSettings.Totp totp = verificationProvider.get().getTotp();
		OptionalLong step = TotpCodec.matchingStep(
				secret,
				code,
				totp.getDigits(),
				clock.currentStep(totp.periodSeconds()),
				totp.getAllowedPastWindows(),
				totp.getAllowedFutureWindows()
		);
		if (step.isEmpty()) return false;

		boolean accepted = persistenceService.acceptCounter(
				enrollment.getUniqueId(),
				enrollment.getMethodId(),
				enrollment.getEnrollmentId(),
				step.getAsLong()
		);
		if (!accepted)
			Logger.debug(
					"TOTP challenge refused a code already used uniqueId=%s provider=%s purpose=%s step=%s",
					context.getSubjectUniqueId(),
					context.getProviderId(),
					context.getPurpose(),
					step.getAsLong()
			);
		return accepted;
	}
}
