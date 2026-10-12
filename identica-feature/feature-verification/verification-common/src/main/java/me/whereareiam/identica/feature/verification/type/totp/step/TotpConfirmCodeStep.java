package me.whereareiam.identica.feature.verification.type.totp.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessContext;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessResult;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessTransition;
import me.whereareiam.identica.feature.verification.process.VerificationProcessStep;
import me.whereareiam.identica.feature.verification.type.process.VerificationProcessStatus;
import me.whereareiam.identica.feature.verification.type.totp.RecoveryCodeGenerator;
import me.whereareiam.identica.feature.verification.type.totp.TotpClock;
import me.whereareiam.identica.feature.verification.type.totp.TotpCodec;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpEnrollmentState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalLong;

@Singleton
public class TotpConfirmCodeStep implements VerificationProcessStep<CodeVerificationInteraction, TotpEnrollmentState> {
	private final Provider<VerificationSettings> verificationProvider;
	private final TotpClock clock;

	@Inject
	public TotpConfirmCodeStep(Provider<VerificationSettings> verificationProvider, TotpClock clock) {
		this.verificationProvider = verificationProvider;
		this.clock = clock;
	}

	@Override
	public @NotNull String id() {
		return "totp-confirm-code";
	}

	@Override
	public @NotNull Class<CodeVerificationInteraction> interactionType() {
		return CodeVerificationInteraction.class;
	}

	@Override
	public @NotNull Class<TotpEnrollmentState> stateType() {
		return TotpEnrollmentState.class;
	}

	@Override
	public @NotNull VerificationProcessResult<TotpEnrollmentState> start(
			@NotNull VerificationProcessContext<TotpEnrollmentState> context
	) {
		return VerificationProcessResult.waiting(context.getState(), VerificationProcessTransition.stay());
	}

	@Override
	public @NotNull VerificationProcessResult<TotpEnrollmentState> submit(
			@NotNull VerificationProcessContext<TotpEnrollmentState> context,
			@NotNull CodeVerificationInteraction interaction
	) {
		TotpEnrollmentState state = context.getState();
		if (state == null)
			return VerificationProcessResult.waiting(null, VerificationProcessTransition.stay());

		OptionalLong step = matchingStep(state.getSecret(), interaction.getCode());
		if (step.isEmpty())
			return VerificationProcessResult.<TotpEnrollmentState>builder()
					.status(VerificationProcessStatus.INVALID)
					.state(state)
					.transition(VerificationProcessTransition.stay())
					.build();

		List<String> recoveryCodes = recoveryCodes();
		state.setVerified(true);
		state.setConfirmedStep(step.getAsLong());
		state.setRecoveryCodes(recoveryCodes);
		return VerificationProcessResult.waiting(state, VerificationProcessTransition.advance());
	}

	private @NotNull OptionalLong matchingStep(@NotNull String secret, @NotNull String code) {
		VerificationSettings.Totp totp = verificationProvider.get().getTotp();
		return TotpCodec.matchingStep(
				secret,
				code,
				totp.getDigits(),
				clock.currentStep(totp.periodSeconds()),
				totp.getAllowedPastWindows(),
				totp.getAllowedFutureWindows()
		);
	}

	private @NotNull List<String> recoveryCodes() {
		VerificationSettings.RecoveryCodes recoveryCodes = verificationProvider.get().getTotp().getRecoveryCodes();
		if (!recoveryCodes.isEnabled()) return List.of();
		return RecoveryCodeGenerator.generateCodes(
				recoveryCodes.getAmount(),
				recoveryCodes.getLength(),
				recoveryCodes.getGroupSize()
		);
	}
}
