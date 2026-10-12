package me.whereareiam.identica.feature.verification.database.mapper;

import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment;

import java.util.Objects;

public final class VerificationEnrollmentMapper {
	public static VerificationEnrollment toModel(VerificationEnrollmentEntity entity) {
		return VerificationEnrollment.builder()
				.uniqueId(entity.getUniqueId())
				.methodId(entity.getMethodId())
				.enrollmentId(entity.getEnrollmentId())
				.credential(entity.getCredential())
				.label(entity.getLabel())
				.createdAt(Objects.requireNonNullElse(entity.getCreatedAt(), 0L))
				.enabledAt(Objects.requireNonNullElse(entity.getEnabledAt(), 0L))
				.acceptedCounter(entity.getAcceptedCounter())
				.build();
	}

	public static VerificationEnrollmentEntity toEntity(VerificationEnrollment model) {
		return VerificationEnrollmentEntity.builder()
				.uniqueId(model.getUniqueId())
				.methodId(model.getMethodId())
				.enrollmentId(model.getEnrollmentId())
				.credential(model.getCredential())
				.label(model.getLabel())
				.createdAt(model.getCreatedAt())
				.enabledAt(model.getEnabledAt())
				.acceptedCounter(model.getAcceptedCounter())
				.build();
	}
}
