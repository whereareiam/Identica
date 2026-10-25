package me.whereareiam.identica.feature.verification.type.totp.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.verification.VerificationMessageDefinitions;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessContext;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessDisplay;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessResult;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessTransition;
import me.whereareiam.identica.feature.verification.process.VerificationProcessStep;
import me.whereareiam.identica.feature.verification.type.process.VerificationProcessStatus;
import me.whereareiam.identica.feature.verification.type.totp.TotpCodec;
import me.whereareiam.identica.feature.verification.type.totp.state.TotpEnrollmentState;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class TotpSetupStep implements VerificationProcessStep<CodeVerificationInteraction, TotpEnrollmentState> {
	private static final int SECRET_BYTES = 20;

	private final Provider<VerificationSettings> verificationProvider;
	private final Provider<VerificationMessages> messagesProvider;

	@Inject
	public TotpSetupStep(Provider<VerificationSettings> verificationProvider, Provider<VerificationMessages> messagesProvider) {
		this.verificationProvider = verificationProvider;
		this.messagesProvider = messagesProvider;
	}

	@Override
	public @NotNull String id() {
		return "totp-setup";
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
		VerificationSettings.Totp totp = verificationProvider.get().getTotp();
		String providerId = context.getProviderId() == null || context.getProviderId().isBlank()
				? "unknown"
				: context.getProviderId();
		String username = context.getUsername() == null || context.getUsername().isBlank()
				? "unknown"
				: context.getUsername();
		String secret = TotpCodec.generateSecret(SECRET_BYTES);
		String label = totp.getLabelFormat()
				.replace("{player}", username)
				.replace("{providerId}", providerId);
		String uri = TotpCodec.buildOtpAuthUri(
				totp.getIssuer(),
				label,
				secret,
				totp.getDigits(),
				totp.periodSeconds()
		);

		TotpEnrollmentState state = TotpEnrollmentState.builder()
				.stepId(id())
				.secret(secret)
				.uri(uri)
				.verified(false)
				.build();

		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("secret", secret);
		placeholders.put("uri", uri);
		placeholders.put("otpauthUri", uri);
		placeholders.put("uriEncoded", URLEncoder.encode(uri, StandardCharsets.UTF_8));

		return VerificationProcessResult.<TotpEnrollmentState>builder()
				.status(VerificationProcessStatus.WAITING)
				.state(state)
				.display(VerificationProcessDisplay.builder()
						.message(VerificationMessageDefinitions.TOTP_PENDING.create(messagesProvider.get()))
						.placeholders(placeholders)
						.build())
				.transition(VerificationProcessTransition.advance())
				.build();
	}

	@Override
	public @NotNull VerificationProcessResult<TotpEnrollmentState> submit(
			@NotNull VerificationProcessContext<TotpEnrollmentState> context,
			@NotNull CodeVerificationInteraction interaction
	) {
		return start(context);
	}
}
