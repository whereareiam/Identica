package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.feature.verification.model.VerificationDisableResult;
import me.whereareiam.identica.feature.verification.model.VerificationResetResult;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentResult;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelectionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Service for enrollment, selection, and verification challenge operations.
 *
 * <p>Provider pipelines use {@link #resolveVerification(VerificationResolutionRequest)}.
 * Commands and external plugins advance pending work by submitting typed
 * {@link VerificationInteraction} instances.</p>
 */
public interface VerificationService {
	/**
	 * Checks whether the provider supports verification and its effective policy enables it.
	 *
	 * @param providerId provider from the current authenticated session
	 * @return whether verification applies to this provider
	 */
	boolean isEnabledForProvider(@NotNull String providerId);

	/**
	 * Resolves the provider verification requirement.
	 *
	 * @param request verification resolution request
	 * @return verification resolution result
	 */
	@NotNull VerificationResolutionResult resolveVerification(@NotNull VerificationResolutionRequest request);

	/**
	 * Submits an interaction to a pending challenge by id.
	 *
	 * @param challengeId challenge id
	 * @param interaction typed interaction
	 * @return challenge result
	 */
	@NotNull VerificationChallengeResult<?> submitChallenge(
			@NotNull String challengeId,
			@NotNull VerificationInteraction interaction
	);

	/**
	 * Submits an interaction to the active challenge for a subject/provider/purpose.
	 *
	 * @param uniqueId subject id
	 * @param providerId provider id
	 * @param purpose challenge purpose
	 * @param interaction typed interaction
	 * @return challenge result
	 */
	@NotNull VerificationChallengeResult<?> submitChallenge(
			@NotNull UUID uniqueId,
			@Nullable String providerId,
			@Nullable String purpose,
			@NotNull VerificationInteraction interaction
	);

	/**
	 * Starts enrollment for a verification method.
	 *
	 * @param uniqueId subject id
	 * @param username current username
	 * @param providerId optional provider context
	 * @param methodId method id
	 * @return enrollment result
	 */
	@NotNull VerificationEnrollmentResult<?> beginEnrollment(
			@NotNull UUID uniqueId,
			@NotNull String username,
			@Nullable String providerId,
			@NotNull String methodId
	);

	/**
	 * Submits an interaction to the pending enrollment for a subject.
	 *
	 * @param uniqueId subject id
	 * @param interaction typed interaction
	 * @return enrollment result
	 */
	@NotNull VerificationEnrollmentResult<?> submitEnrollment(
			@NotNull UUID uniqueId,
			@NotNull VerificationInteraction interaction
	);

	/**
	 * Cancels pending enrollment for a subject.
	 *
	 * @param uniqueId subject id
	 * @return {@code true} when a pending enrollment was removed
	 */
	boolean cancelPendingEnrollment(@NotNull UUID uniqueId);

	/**
	 * Returns enrolled verification methods for a subject.
	 *
	 * @param uniqueId subject id
	 * @return enrolled methods
	 */
	@NotNull List<VerificationEnrollment> findEnrollments(@NotNull UUID uniqueId);

	/**
	 * Returns provider-specific method selections for a subject.
	 *
	 * @param uniqueId subject id
	 * @return selections
	 */
	@NotNull List<VerificationSelection> findSelections(@NotNull UUID uniqueId);

	/**
	 * Selects a method for a provider.
	 *
	 * @param uniqueId subject id
	 * @param providerId provider id
	 * @param methodId method id
	 * @return selection result
	 */
	@NotNull VerificationSelectionResult selectMethod(
			@NotNull UUID uniqueId,
			@NotNull String providerId,
			@NotNull String methodId
	);

	/**
	 * Disables an enrolled method.
	 *
	 * @param uniqueId subject id
	 * @param methodId method id
	 * @return disable result
	 */
	@NotNull VerificationDisableResult disableMethod(
			@NotNull UUID uniqueId,
			@NotNull String methodId
	);

	/**
	 * Resets verification state.
	 *
	 * @param uniqueId subject id
	 * @param providerId optional provider id
	 * @return reset result
	 */
	@NotNull VerificationResetResult reset(
			@NotNull UUID uniqueId,
			@Nullable String providerId
	);
}
