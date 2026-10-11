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

	@NotNull Optional<VerificationSelection> findSelection(@NotNull UUID uniqueId, @NotNull String providerId);

	@NotNull List<VerificationSelection> findSelections(@NotNull UUID uniqueId);

	@NotNull VerificationSelection upsertSelection(@NotNull VerificationSelection selection);

	void deleteSelection(@NotNull UUID uniqueId, @NotNull String providerId);

	void deleteSelectionsByMethod(@NotNull UUID uniqueId, @NotNull String methodId);

	@NotNull List<VerificationRecoveryCode> findRecoveryCodes(@NotNull UUID uniqueId, @NotNull String methodId);

	void replaceRecoveryCodes(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull List<VerificationRecoveryCode> recoveryCodes);

	boolean markRecoveryCodeUsed(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull String codeHash, long usedAt);

	/**
	 * Accepts the time step of a time-based one-time code for an account's method, so that each code is used at
	 * most once. The step is accepted only when it is newer than every step accepted before for the same account
	 * and method, and then becomes the last accepted one. The check and the write are a single conditional write,
	 * so of concurrent calls with the same step, also from different proxies sharing the database, only one
	 * returns {@code true}. Deleting the method's enrollment forgets its last accepted step.
	 *
	 * @param uniqueId account unique id
	 * @param methodId verification method id
	 * @param timeStep time step the submitted code belongs to
	 * @return {@code true} when the step was accepted now; {@code false} when it, or a newer one, was accepted before
	 */
	boolean acceptTimeStep(@NotNull UUID uniqueId, @NotNull String methodId, long timeStep);

	void deleteAll(@NotNull UUID uniqueId);

	void deleteProviderSelections(@NotNull UUID uniqueId, @Nullable String providerId);
}
