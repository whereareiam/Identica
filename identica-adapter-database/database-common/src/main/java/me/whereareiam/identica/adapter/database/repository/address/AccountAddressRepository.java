package me.whereareiam.identica.adapter.database.repository.address;

import me.whereareiam.dialectica.adapter.jdbi.entity.RegisterDialecticaMapper;
import me.whereareiam.identica.adapter.database.entity.address.AccountAddressEntity;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.UUID;

/**
 * The address queries beyond reading and writing one row, which {@code EntityAccess<AccountAddressEntity>} does.
 */
@RegisterDialecticaMapper(AccountAddressEntity.class)
public interface AccountAddressRepository {
	@SqlQuery("""
			SELECT first_seen_at FROM identica_account_addresses
			 WHERE network = :network
			   AND is_registration = :registration
			   AND first_seen_at >= :since
			 ORDER BY first_seen_at
			""")
	List<Long> findFirstSeenTimes(@Bind("network") String network, @Bind("registration") boolean registration, @Bind("since") long since);

	@SqlQuery("SELECT * FROM identica_account_addresses")
	List<AccountAddressEntity> findAll();

	/**
	 * Finds the addresses of an account it did not register from, the most recently used first.
	 */
	@SqlQuery("""
			SELECT address FROM identica_account_addresses
			 WHERE unique_id = :uniqueId
			   AND is_registration = :registration
			 ORDER BY last_seen_at DESC, address
			""")
	List<String> findAddressesByLastSeen(@Bind("uniqueId") UUID uniqueId, @Bind("registration") boolean registration);

	@SqlUpdate("""
			UPDATE identica_account_addresses
			   SET address = :newAddress,
			       network = :network
			 WHERE unique_id = :uniqueId
			   AND address = :address
			""")
	int replace(
			@Bind("uniqueId") UUID uniqueId,
			@Bind("address") String address,
			@Bind("newAddress") String newAddress,
			@Bind("network") String network
	);

	@SqlUpdate("""
			DELETE FROM identica_account_addresses
			 WHERE is_registration = :registration
			   AND last_seen_at < :before
			""")
	int deleteLastSeenBefore(@Bind("registration") boolean registration, @Bind("before") long before);

	@SqlUpdate("""
			UPDATE identica_account_addresses
			   SET is_registration = :expired
			 WHERE is_registration = :registration
			   AND first_seen_at < :before
			""")
	int expireFirstSeenBefore(@Bind("registration") boolean registration, @Bind("expired") boolean expired, @Bind("before") long before);
}
