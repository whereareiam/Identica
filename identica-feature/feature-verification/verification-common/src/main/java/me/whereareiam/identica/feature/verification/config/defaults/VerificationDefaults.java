package me.whereareiam.identica.feature.verification.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;

import java.time.Duration;

@Singleton
public class VerificationDefaults implements DefaultsProvider<VerificationSettings> {
	@Override
	public VerificationSettings supply(VerificationSettings verification) {
		verification.setDefaults(new VerificationProvidersDefaults().supply(new me.whereareiam.identica.feature.verification.model.config.VerificationProviders.Verification()));
		verification.setChallengeTtl(Duration.ofMinutes(2));
		verification.setEnrollmentTtl(Duration.ofMinutes(10));
		verification.setAutoSelectCurrentProvider(true);

		VerificationSettings.Totp totp = new VerificationSettings.Totp();
		totp.setDisplayName("Authenticator App");
		totp.setIssuer("Identica");
		totp.setLabelFormat("{player}@{providerId}");
		totp.setDigits(6);
		totp.setPeriod(Duration.ofSeconds(30));
		totp.setAllowedPastWindows(1);
		totp.setAllowedFutureWindows(1);

		VerificationSettings.RecoveryCodes recoveryCodes = new VerificationSettings.RecoveryCodes();
		recoveryCodes.setEnabled(true);
		recoveryCodes.setAmount(8);
		recoveryCodes.setLength(10);
		recoveryCodes.setGroupSize(4);
		totp.setRecoveryCodes(recoveryCodes);

		verification.setTotp(totp);
		return verification;
	}
}
