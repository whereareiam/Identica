package me.whereareiam.identica.feature.verification.database.repository;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.feature.verification.database.entity.VerificationSelectionEntity;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.UUID;

/**
 * The selection queries beyond reading and writing one selection by its key, which
 * {@code EntityAccess<VerificationSelectionEntity>} does.
 */
@RegisterDialecticaMapper(VerificationSelectionEntity.class)
public interface VerificationSelectionRepository {
	@SqlQuery("SELECT * FROM identica_verification_selections WHERE unique_id = :uniqueId")
	List<VerificationSelectionEntity> findByUniqueId(@Bind("uniqueId") UUID uniqueId);

	@SqlUpdate("""
			DELETE FROM identica_verification_selections
			 WHERE unique_id = :uniqueId
			   AND method_id = :methodId
			""")
	void deleteSelectionsByMethod(@Bind("uniqueId") UUID uniqueId, @Bind("methodId") String methodId);

	@SqlUpdate("""
			DELETE FROM identica_verification_selections
			 WHERE unique_id = :uniqueId
			""")
	void deleteAll(@Bind("uniqueId") UUID uniqueId);
}
