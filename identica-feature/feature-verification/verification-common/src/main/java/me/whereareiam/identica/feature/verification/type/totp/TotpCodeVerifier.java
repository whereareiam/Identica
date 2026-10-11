package me.whereareiam.identica.feature.verification.type.totp;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Accepts each authenticator code at most once per account and method (RFC 6238, section 5.2). A code is
 * accepted when it matches a time step within the allowed clock drift and that step is newer than the last
 * step accepted for the account and method; accepting it makes it the last one. The last step is kept in the
 * database every proxy shares and advanced by a conditional write, so of two proxies given the same code at
 * the same moment only one accepts it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TotpCodeVerifier {
	private final Provider<VerificationSettings> verificationProvider;
	private final VerificationPersistenceService persistenceService;

	/**
	 * Accepts a code of the given secret for the account and method unless it does not match or its time step
	 * was already used.
	 *
	 * @return whether the code was accepted now
	 */
	public boolean accept(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull String secret, @NotNull String code) {
		VerificationSettings.Totp totp = verificationProvider.get().getTotp();
		OptionalLong step = TotpCodec.matchingStep(
				secret,
				code,
				totp.getDigits(),
				totp.periodSeconds(),
				totp.getAllowedPastWindows(),
				totp.getAllowedFutureWindows()
		);
		if (step.isEmpty()) return false;
		if (persistenceService.acceptTimeStep(uniqueId, methodId, step.getAsLong())) return true;

		Logger.debug("TOTP code refused as already used uniqueId=%s method=%s step=%s", uniqueId, methodId, step.getAsLong());
		return false;
	}
}
