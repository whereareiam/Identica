package me.whereareiam.identica.feature.verification.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
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
	private final VerificationEnrollmentRepository enrollmentRepository;
	private final VerificationSelectionRepository selectionRepository;
	private final VerificationRecoveryCodeRepository recoveryCodeRepository;

	@Inject
	public DefaultVerificationPersistenceService(
			VerificationEnrollmentRepository enrollmentRepository,
			VerificationSelectionRepository selectionRepository,
			VerificationRecoveryCodeRepository recoveryCodeRepository
	) {
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
	public @NotNull VerificationEnrollment upsertEnrollment(@NotNull VerificationEnrollment enrollment) {
		Optional<VerificationEnrollment> existing = enrollmentRepository.find(
						enrollment.getUniqueId(),
						enrollment.getMethodId(),
						enrollment.getEnrollmentId()
				)
				.map(VerificationEnrollmentMapper::toModel);
		if (existing.isPresent()) {
			enrollmentRepository.update(
					enrollment.getUniqueId(),
					enrollment.getMethodId(),
					enrollment.getEnrollmentId(),
					enrollment.getCredential(),
					enrollment.getLabel(),
					enrollment.getCreatedAt(),
					enrollment.getEnabledAt()
			);
			return enrollment;
		}

		enrollmentRepository.insert(
				enrollment.getUniqueId(),
				enrollment.getMethodId(),
				enrollment.getEnrollmentId(),
				enrollment.getCredential(),
				enrollment.getLabel(),
				enrollment.getCreatedAt(),
				enrollment.getEnabledAt()
		);
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
		return selectionRepository.find(uniqueId, providerId).map(VerificationSelectionMapper::toModel);
	}

	@Override
	public @NotNull List<VerificationSelection> findSelections(@NotNull UUID uniqueId) {
		return selectionRepository.findByUniqueId(uniqueId).stream()
				.map(VerificationSelectionMapper::toModel)
				.toList();
	}

	@Override
	public @NotNull VerificationSelection upsertSelection(@NotNull VerificationSelection selection) {
		Optional<VerificationSelection> existing = findSelection(selection.getUniqueId(), selection.getProviderId());
		if (existing.isPresent()) {
			selectionRepository.update(
					selection.getUniqueId(),
					selection.getProviderId(),
					selection.getMethodId(),
					selection.getSelectedAt()
			);
			return selection;
		}

		selectionRepository.insert(
				selection.getUniqueId(),
				selection.getProviderId(),
				selection.getMethodId(),
				selection.getSelectedAt()
		);
		return selection;
	}

	@Override
	public void deleteSelection(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) return;
		selectionRepository.delete(uniqueId, providerId);
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
		for (VerificationRecoveryCode recoveryCode : recoveryCodes) {
			recoveryCodeRepository.insert(
					recoveryCode.getUniqueId(),
					recoveryCode.getMethodId(),
					recoveryCode.getCodeHash(),
					recoveryCode.getCreatedAt(),
					recoveryCode.getUsedAt()
			);
		}
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

		selectionRepository.delete(uniqueId, providerId);
	}

	@IdenticEvent(EventOrder.HIGH)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;
		deleteAll(uniqueId);
	}
}
