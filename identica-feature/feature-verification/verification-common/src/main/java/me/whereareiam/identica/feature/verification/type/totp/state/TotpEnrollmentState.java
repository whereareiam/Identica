package me.whereareiam.identica.feature.verification.type.totp.state;

import lombok.*;
import me.whereareiam.identica.feature.verification.process.VerificationStepCursor;
import me.whereareiam.identica.feature.verification.state.VerificationEnrollmentState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TotpEnrollmentState implements VerificationEnrollmentState, VerificationStepCursor {
	private @NotNull String stepId;
	private @NotNull String secret;
	private @NotNull String uri;
	@Builder.Default
	private @NotNull List<String> recoveryCodes = new ArrayList<>();
	private boolean verified;
	/** Time step of the code that confirmed the enrollment, which the enrollment starts with as accepted. */
	private long confirmedStep;

	@Override
	public String credential() {
		return secret;
	}

	@Override
	public String label() {
		return "Authenticator App";
	}

	@Override
	public long acceptedCounter() {
		return confirmedStep;
	}
}
