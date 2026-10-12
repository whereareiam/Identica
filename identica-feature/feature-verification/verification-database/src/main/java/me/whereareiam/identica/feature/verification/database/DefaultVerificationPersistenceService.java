package me.whereareiam.identica.feature.verification.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import me.whereareiam.identica.feature.verification.database.entity.VerificationRecoveryCodeEntity;
import me.whereareiam.identica.feature.verification.database.entity.VerificationSelectionEntity;
import me.whereareiam.identica.feature.verification.database.mapper.VerificationEnrollmentMapper;
import me.whereareiam.identica.feature.verification.database.mapper.VerificationRecoveryCodeMapper;
import me.whereareiam.identica.feature.verification.database.mapper.VerificationSelectionMapper;
import me.whereareiam.identica.feature.verification.database.repository.VerificationEnrollmentRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationRecoveryCodeRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationSelectionRepository;
import me.whereareiam.identica.feature.verification.model.VerificationRecoveryCode;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;
import me.whereareiam.identica.feature.verification.model.selection.VerificationSelection;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultVerificationPersistenceService implements VerificationPersistenceService, EventListener {
	private final EntityAccess<VerificationEnrollmentEntity> enrollments;
	private final EntityAccess<VerificationSelectionEntity> selections;
	private final EntityAccess<VerificationRecoveryCodeEntity> recoveryCodes;
	private final VerificationEnrollmentRepository enrollmentRepository;
	private final VerificationSelectionRepository selectionRepository;
	private final VerificationRecoveryCodeRepository recoveryCodeRepository;

	@Inject
	public DefaultVerificationPersistenceService(
			EntityAccess<VerificationEnrollmentEntity> enrollments,
			EntityAccess<VerificationSelectionEntity> selections,
			EntityAccess<VerificationRecoveryCodeEntity> recoveryCodes,
			VerificationEnrollmentRepository enrollmentRepository,
			VerificationSelectionRepository selectionRepository,
			VerificationRecoveryCodeRepository recoveryCodeRepository
	) {
		this.enrollments = enrollments;
		this.selections = selections;
		this.recoveryCodes = recoveryCodes;
		this.enrollmentRepository = enrollmentRepository;
		this.selectionRepository = selectionRepository;
		this.recoveryCodeRepository = recoveryCodeRepository;
	}

	@Override
	public @NotNull Optional<VerificationEnrollment> findEnrollment(@NotNull UUID uniqueId, @NotNull String methodId) {
		if (methodId.isBlank()) return Optional.empty();
		return enrollmentRepository.find(uniqueId, methodId).map(VerificationEnrollmentMapper::toModel);
	}

	@Override
	public @NotNull List<VerificationEnrollment> findEnrollments(@NotNull UUID uniqueId) {
		return enrollmentRepository.findByUniqueId(uniqueId).stream()
				.map(VerificationEnrollmentMapper::toModel)
				.toList();
	}

	@Override
	public long countEnrolledAccounts() {
		return enrollmentRepository.countAccounts();
	}

	@Override
	public @NotNull VerificationEnrollment upsertEnrollment(@NotNull VerificationEnrollment enrollment) {
		VerificationEnrollmentEntity entity = VerificationEnrollmentMapper.toEntity(enrollment);
		if (enrollments.find(enrollment.getUniqueId(), enrollment.getMethodId(), enrollment.getEnrollmentId()).isPresent()) {
			enrollments.update(entity);
			return enrollment;
		}

		enrollments.insert(entity);
		return enrollment;
	}

	@Override
	public void deleteEnrollment(@NotNull UUID uniqueId, @NotNull String methodId) {
		if (methodId.isBlank()) return;
		recoveryCodeRepository.deleteByMethod(uniqueId, methodId);
		enrollmentRepository.delete(uniqueId, methodId);
	}

	@Override
	public @NotNull Optional<VerificationSelection> findSelection(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) return Optional.empty();
		return selections.find(uniqueId, providerId).map(VerificationSelectionMapper::toModel);
	}

	@Override
	public @NotNull List<VerificationSelection> findSelections(@NotNull UUID uniqueId) {
		return selectionRepository.findByUniqueId(uniqueId).stream()
				.map(VerificationSelectionMapper::toModel)
				.toList();
	}

	@Override
	public @NotNull VerificationSelection upsertSelection(@NotNull VerificationSelection selection) {
		VerificationSelectionEntity entity = VerificationSelectionMapper.toEntity(selection);
		Optional<VerificationSelection> existing = findSelection(selection.getUniqueId(), selection.getProviderId());
		if (existing.isPresent()) {
			selections.update(entity);
			return selection;
		}

		selections.insert(entity);
		return selection;
	}

	@Override
	public void deleteSelection(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) return;
		selections.delete(uniqueId, providerId);
	}

	@Override
	public void deleteSelectionsByMethod(@NotNull UUID uniqueId, @NotNull String methodId) {
		if (methodId.isBlank()) return;
		selectionRepository.deleteSelectionsByMethod(uniqueId, methodId);
	}

	@Override
	public @NotNull List<VerificationRecoveryCode> findRecoveryCodes(@NotNull UUID uniqueId, @NotNull String methodId) {
		if (methodId.isBlank()) return List.of();
		return recoveryCodeRepository.findByUniqueIdAndMethod(uniqueId, methodId).stream()
				.map(VerificationRecoveryCodeMapper::toModel)
				.toList();
	}

	@Override
	public void replaceRecoveryCodes(
			@NotNull UUID uniqueId,
			@NotNull String methodId,
			@NotNull List<VerificationRecoveryCode> recoveryCodes
	) {
		if (methodId.isBlank()) return;
		recoveryCodeRepository.deleteByMethod(uniqueId, methodId);
		for (VerificationRecoveryCode recoveryCode : recoveryCodes)
			this.recoveryCodes.insert(VerificationRecoveryCodeMapper.toEntity(recoveryCode));
	}

	@Override
	public boolean markRecoveryCodeUsed(@NotNull UUID uniqueId, @NotNull String methodId, @NotNull String codeHash, long usedAt) {
		if (methodId.isBlank() || codeHash.isBlank()) return false;
		return recoveryCodeRepository.markUsed(uniqueId, methodId, codeHash, usedAt) > 0;
	}

	@Override
	public void deleteAll(@NotNull UUID uniqueId) {
		recoveryCodeRepository.deleteAll(uniqueId);
		selectionRepository.deleteAll(uniqueId);
		enrollmentRepository.deleteAll(uniqueId);
	}

	@Override
	public void deleteProviderSelections(@NotNull UUID uniqueId, @Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) {
			selectionRepository.deleteAll(uniqueId);
			return;
		}

		selections.delete(uniqueId, providerId);
	}

	@IdenticEvent(EventOrder.HIGH)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;
		deleteAll(uniqueId);
	}
}
