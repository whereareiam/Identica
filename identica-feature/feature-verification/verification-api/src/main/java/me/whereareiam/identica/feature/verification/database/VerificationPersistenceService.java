package me.whereareiam.identica.feature.verification.database;

import me.whereareiam.identica.feature.verification.model.VerificationRecoveryCode;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationPersistenceService {
	@NotNull Optional<VerificationEnrollment> findEnrollment(@NotNull UUID uniqueId, @NotNull String methodId);

	@NotNull List<VerificationEnrollment> findEnrollments(@NotNull UUID uniqueId);

	@NotNull VerificationEnrollment upsertEnrollment(@NotNull VerificationEnrollment enrollment);

	void deleteEnrollment(@NotNull UUID uniqueId, @NotNull String methodId);

	/**
	 * Raises an enrollment's {@linkplain VerificationEnrollment#getAcceptedCounter() accepted counter}
	 * to a counter, when the counter is greater than the one accepted last. The check and the write
	 * are one statement, so of several proxies offering the same counter at once, exactly one
	 * succeeds.
	 *
	 * @param uniqueId     account of the enrollment
	 * @param methodId     method of the enrollment
	 * @param enrollmentId id of the enrollment
	 * @param counter      counter of the code to accept
	 * @return whether the counter was accepted; {@code false} when it, or a greater one, was
	 *         accepted already, or the enrollment does not exist
	 */
	boolean acceptCounter(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull String enrollmentId, long counter);

	/**
	 * Counts the accounts with at least one verification method enabled.
	 *
	 * @return the number of accounts
	 */
	long countEnrolledAccounts();

	@NotNull Optional<VerificationSelection> findSelection(@NotNull UUID uniqueId, @NotNull String providerId);

	@NotNull List<VerificationSelection> findSelections(@NotNull UUID uniqueId);

	@NotNull VerificationSelection upsertSelection(@NotNull VerificationSelection selection);

	void deleteSelection(@NotNull UUID uniqueId, @NotNull String providerId);

	void deleteSelectionsByMethod(@NotNull UUID uniqueId, @NotNull String methodId);

	@NotNull List<VerificationRecoveryCode> findRecoveryCodes(@NotNull UUID uniqueId, @NotNull String methodId);

	void replaceRecoveryCodes(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull List<VerificationRecoveryCode> recoveryCodes);

	boolean markRecoveryCodeUsed(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull String codeHash, long usedAt);

	void deleteAll(@NotNull UUID uniqueId);

	void deleteProviderSelections(@NotNull UUID uniqueId, @Nullable String providerId);
}
