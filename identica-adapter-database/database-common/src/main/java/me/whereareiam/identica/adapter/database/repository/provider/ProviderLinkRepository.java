package me.whereareiam.identica.adapter.database.repository.provider;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The provider link queries beyond reading and writing one link by its provider subject, which
 * {@code EntityAccess<AccountProviderLinkEntity>} does.
 */
@RegisterDialecticaMapper(AccountProviderLinkEntity.class)
public interface ProviderLinkRepository {
	@SqlQuery("""
			SELECT *
			  FROM identica_provider_links
			 WHERE unique_id = :uniqueId
			   AND provider_id = :providerId
			""")
	Optional<AccountProviderLinkEntity> findByUniqueIdAndProviderId(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("providerId") String providerId
	);

	@SqlQuery("SELECT * FROM identica_provider_links WHERE unique_id = :uniqueId")
	List<AccountProviderLinkEntity> findByUniqueId(@Bind("uniqueId") UUID uniqueId);

	@SqlQuery("""
			SELECT provider_id AS providerId,
			       COUNT(*) AS usageCount
			  FROM identica_provider_links
			 GROUP BY provider_id
			""")
	@KeyColumn("providerId")
	@ValueColumn("usageCount")
	Map<String, Long> countByProvider();

	@SqlUpdate("""
			UPDATE identica_provider_links
			   SET is_primary = :primary
			 WHERE unique_id = :uniqueId
			   AND provider_id = :providerId
			""")
	void updatePrimary(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("providerId") String providerId,
			@Bind("primary") boolean primary
	);

	@SqlUpdate("""
			UPDATE identica_provider_links
			   SET is_primary = :primary
			 WHERE unique_id = :uniqueId
			""")
	void clearPrimary(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("primary") boolean primary
	);

	@SqlUpdate("""
			DELETE FROM identica_provider_links
			 WHERE unique_id = :uniqueId
			""")
	void deleteAll(@Bind("uniqueId") UUID uniqueId);

	@SqlUpdate("""
			DELETE FROM identica_provider_links
			 WHERE unique_id = :uniqueId
			   AND provider_id = :providerId
			""")
	void delete(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("providerId") String providerId
	);
}
