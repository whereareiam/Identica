package me.whereareiam.identica.feature.verification.model.enrollment;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
/**
 * An enabled verification method of an account.
 */
public class VerificationEnrollment {
	private UUID uniqueId;
	private String methodId;
	private String enrollmentId;
	private String credential;
	private String label;
	private long createdAt;
	private long enabledAt;
	/**
	 * The last counter a code of this enrollment was accepted for: the TOTP time step or the HOTP
	 * counter. It never decreases, and a code is only accepted for a greater counter, so each code
	 * is accepted once.
	 */
	private long acceptedCounter;
}
