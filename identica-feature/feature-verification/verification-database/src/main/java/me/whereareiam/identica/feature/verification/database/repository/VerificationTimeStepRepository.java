package me.whereareiam.identica.feature.verification.database.repository;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTimeStepRepository {
	@SqlQuery("""
			SELECT last_step
			  FROM identica_verification_time_steps
			 WHERE unique_id = :uniqueId
			   AND method_id = :methodId
			""")
	Optional<Long> find(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId);

	@SqlUpdate("""
			UPDATE identica_verification_time_steps
			   SET last_step = :step
			 WHERE unique_id = :uniqueId
			   AND method_id = :methodId
			   AND last_step < :step
			""")
	int advance(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId, @Bind("step") long step);

	@SqlUpdate("""
			INSERT INTO identica_verification_time_steps (
				unique_id, method_id, last_step
			) VALUES (
				:uniqueId, :methodId, :step
			)
			""")
	void insert(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId, @Bind("step") long step);

	@SqlUpdate("""
			DELETE FROM identica_verification_time_steps
			 WHERE unique_id = :uniqueId
			   AND method_id = :methodId
			""")
	void delete(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId);

	@SqlUpdate("""
			DELETE FROM identica_verification_time_steps
			 WHERE unique_id = :uniqueId
			""")
	void deleteAll(@Bind("uniqueId") UUID uniqueId);
}
