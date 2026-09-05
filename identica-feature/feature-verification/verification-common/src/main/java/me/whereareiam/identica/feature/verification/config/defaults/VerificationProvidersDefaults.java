package me.whereareiam.identica.feature.verification.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.feature.verification.model.config.VerificationProviders;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;

import java.util.List;

@Singleton
public class VerificationProvidersDefaults implements DefaultsProvider<VerificationProviders.Verification> {
	@Override
	public VerificationProviders.Verification supply(VerificationProviders.Verification verification) {
		verification.setEnabled(true);
		verification.setRequired(false);
		verification.setUnavailableSelectionPolicy(UnavailableSelectionPolicy.KEEP_LOCKED);

		VerificationProviders.Verification.MethodEntry totp = new VerificationProviders.Verification.MethodEntry();
		totp.setId("totp");
		totp.setEnabled(true);
		totp.setPriority(100);
		verification.setMethods(List.of(totp));
		return verification;
	}
}
