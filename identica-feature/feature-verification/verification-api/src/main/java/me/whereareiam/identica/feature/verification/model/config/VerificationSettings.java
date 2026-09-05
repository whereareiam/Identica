package me.whereareiam.identica.feature.verification.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** Shared verification settings; provider policy fields can be overridden per provider. */
@Getter
@Setter
@ToString
public class VerificationSettings extends ConfigDocument {
	/** Provider policy inherited when an override is absent. */
	private @NotNull VerificationProviders.Verification defaults = new VerificationProviders.Verification();
	private @NotNull Duration challengeTtl;
	private @NotNull Duration enrollmentTtl;
	private boolean autoSelectCurrentProvider;
	private @NotNull Totp totp;

	/** @return positive challenge lifetime in milliseconds; rejects non-positive durations */
	public long challengeTtlMillis() {
		if (challengeTtl.isZero() || challengeTtl.isNegative())
			throw new IllegalStateException("verification.challengeTtl must be positive");

		return challengeTtl.toMillis();
	}

	/** @return positive enrollment lifetime in milliseconds; rejects non-positive durations */
	public long enrollmentTtlMillis() {
		if (enrollmentTtl.isZero() || enrollmentTtl.isNegative())
			throw new IllegalStateException("verification.enrollmentTtl must be positive");

		return enrollmentTtl.toMillis();
	}

	/** Shared authenticator labels, code parameters, and recovery-code generation settings. */
	@Getter
	@Setter
	@ToString
	public static class Totp {
		private @NotNull String displayName;
		private @NotNull String issuer;
		private @NotNull String labelFormat;
		private int digits;
		private @NotNull Duration period;
		private int allowedPastWindows;
		private int allowedFutureWindows;
		private @NotNull RecoveryCodes recoveryCodes;

		/** @return positive TOTP time-step duration in whole seconds */
		public long periodSeconds() {
			if (period.isZero() || period.isNegative())
				throw new IllegalStateException("verification.totp.period must be positive");

			return period.getSeconds();
		}
	}

	/** Settings for generating and formatting recovery-code batches. */
	@Getter
	@Setter
	@ToString
	public static class RecoveryCodes {
		private boolean enabled;
		private int amount;
		private int length;
		private int groupSize;
	}
}
