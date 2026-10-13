package me.whereareiam.identica.feature.verification.type.status;

/**
 * Result status for resolving a provider verification requirement.
 */
public enum VerificationResolutionStatus {
	SATISFIED,
	WAITING,
	DENIED,
	SKIPPED,
	FAILED,
	/**
	 * The active challenge did not accept the code of the request and the requirement stays unresolved. The result
	 * names the reason in {@link me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult#getRejection()}.
	 */
	REJECTED
}
