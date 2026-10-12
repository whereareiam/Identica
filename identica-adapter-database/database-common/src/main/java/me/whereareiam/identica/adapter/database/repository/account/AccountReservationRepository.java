package me.whereareiam.identica.adapter.database.repository.account;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.UUID;

/**
 * The reservation queries beyond reading and writing one reservation by its key, which
 * {@code EntityAccess<AccountReservationEntity>} does.
 */
public interface AccountReservationRepository {
	@SqlUpdate("""
			DELETE FROM identica_account_reservations
			 WHERE unique_id = :uniqueId
			""")
	void deleteByUniqueId(@Bind("uniqueId") UUID uniqueId);

	@SqlUpdate("""
			DELETE FROM identica_account_reservations
			 WHERE expires_at <= :now
			""")
	void deleteExpired(@Bind("now") long now);
}
