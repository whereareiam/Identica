package me.whereareiam.identica.feature.verification.model.resolution;

import lombok.*;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Result returned when resolving the provider verification requirement.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class VerificationResolutionResult {
	private VerificationResolutionStatus status;
	private String challengeId;
	private String methodId;
	private boolean required;
	private boolean recoveryCodeUsed;
	private @Nullable String message;
	/**
	 * Why the active challenge did not accept the code of the request, set when the status is
	 * {@link VerificationResolutionStatus#REJECTED REJECTED}: {@link VerificationChallengeStatus#INVALID INVALID} for a
	 * wrong code, another status when there was no challenge to take it.
	 */
	private @Nullable VerificationChallengeStatus rejection;

	/**
	 * Creates a resolution result.
	 *
	 * @param status resolution status
	 * @param challengeId challenge id
	 * @param methodId method id
	 * @param required whether verification is required
	 * @param recoveryCodeUsed whether a recovery code was used
	 * @return resolution result
	 */
	public static VerificationResolutionResult of(
			VerificationResolutionStatus status,
			@Nullable String challengeId,
			@Nullable String methodId,
			boolean required,
			boolean recoveryCodeUsed
	) {
		return VerificationResolutionResult.builder()
				.status(status)
				.challengeId(challengeId)
				.methodId(methodId)
				.required(required)
				.recoveryCodeUsed(recoveryCodeUsed)
				.build();
	}
}
