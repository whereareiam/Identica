package me.whereareiam.identica.feature.verification.database.repository;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The enrollment queries beyond reading and writing one enrollment by its key, which
 * {@code EntityAccess<VerificationEnrollmentEntity>} does.
 */
@RegisterDialecticaMapper(VerificationEnrollmentEntity.class)
public interface VerificationEnrollmentRepository {
	@SqlQuery("""
			SELECT *
			  FROM identica_verification_enrollments
			 WHERE unique_id = :uniqueId
			   AND method_id = :methodId
			 ORDER BY enabled_at DESC
			 LIMIT 1
			""")
	Optional<VerificationEnrollmentEntity> find(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId);

	@SqlQuery("SELECT * FROM identica_verification_enrollments WHERE unique_id = :uniqueId")
	List<VerificationEnrollmentEntity> findByUniqueId(@Bind("uniqueId") UUID uniqueId);

	@SqlQuery("SELECT COUNT(DISTINCT unique_id) FROM identica_verification_enrollments")
	long countAccounts();

	@SqlUpdate("""
			DELETE FROM identica_verification_enrollments
			 WHERE unique_id = :uniqueId
			   AND method_id = :methodId
			""")
	void delete(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId);

	@SqlUpdate("""
			DELETE FROM identica_verification_enrollments
			 WHERE unique_id = :uniqueId
			""")
	void deleteAll(@Bind("uniqueId") UUID uniqueId);
}
