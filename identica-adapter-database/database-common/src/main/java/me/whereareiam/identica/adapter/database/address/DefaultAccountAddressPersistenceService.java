package me.whereareiam.identica.adapter.database.address;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.address.AccountAddressEntity;
import me.whereareiam.identica.adapter.database.mapper.address.AccountAddressMapper;
import me.whereareiam.identica.adapter.database.repository.address.AccountAddressRepository;
import me.whereareiam.identica.model.identity.address.AccountAddress;
import me.whereareiam.identica.persistence.address.AccountAddressPersistenceService;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the addresses of accounts in {@code identica_account_addresses}.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountAddressPersistenceService implements AccountAddressPersistenceService {
	private static final String NETWORK = "network";
	private static final String LAST_SEEN_AT = "last_seen_at";
	private static final String REGISTRATION = "is_registration";

	private final Provider<Jdbi> jdbi;
	private final Provider<EntityAccess<AccountAddressEntity>> addresses;
	private final Provider<AccountAddressRepository> repository;

	/**
	 * Inserts the address, or updates the one the account used before; an earlier first use is kept, and only a
	 * registration marks the address as the account's registration address.
	 */
	@Override
	public void record(@NotNull AccountAddress address) {
		AccountAddressEntity entity = AccountAddressMapper.toEntity(address);
		if (address.isRegistration()) addresses.get().upsert(entity, NETWORK, LAST_SEEN_AT, REGISTRATION);
		else addresses.get().upsert(entity, NETWORK, LAST_SEEN_AT);
	}

	@Override
	public @NotNull List<Long> findRegistrationTimes(@NotNull String network, long since) {
		return repository.get().findFirstSeenTimes(network, true, since);
	}

	@Override
	public @NotNull List<AccountAddress> findAll() {
		return repository.get().findAll().stream().map(AccountAddressMapper::toModel).toList();
	}

	/**
	 * Replaces the stored forms in one transaction. When the account already has the new form, for example an
	 * address recorded hashed before and as it is later, the two rows become one: the earliest first use, the
	 * latest last use, and a registration address when either was.
	 */
	@Override
	public void replace(@NotNull UUID uniqueId, @NotNull String address, @NotNull AccountAddress stored) {
		if (address.equals(stored.getAddress())) {
			repository.get().replace(uniqueId, address, stored.getAddress(), stored.getNetwork());
			return;
		}

		jdbi.get().useTransaction(handle -> {
			Optional<AccountAddressEntity> existing = addresses.get().find(uniqueId, stored.getAddress());
			if (existing.isEmpty()) {
				repository.get().replace(uniqueId, address, stored.getAddress(), stored.getNetwork());
				return;
			}

			AccountAddressEntity kept = existing.get();
			addresses.get().update(uniqueId, stored.getAddress())
					.set(NETWORK, stored.getNetwork())
					.set(REGISTRATION, kept.isRegistration() || stored.isRegistration())
					.set("first_seen_at", Math.min(kept.getFirstSeenAt(), stored.getFirstSeenAt()))
					.set(LAST_SEEN_AT, Math.max(kept.getLastSeenAt(), stored.getLastSeenAt()))
					.execute();
			addresses.get().delete(uniqueId, address);
		});
	}

	@Override
	public void delete(@NotNull UUID uniqueId, @NotNull String address) {
		addresses.get().delete(uniqueId, address);
	}

	@Override
	public int deleteIdle(long lastSeenBefore) {
		return repository.get().deleteLastSeenBefore(false, lastSeenBefore);
	}

	@Override
	public int expireRegistrations(long registeredBefore) {
		return repository.get().expireFirstSeenBefore(true, false, registeredBefore);
	}

	@Override
	public int trim(@NotNull UUID uniqueId, int keep) {
		List<String> newestFirst = repository.get().findAddressesByLastSeen(uniqueId, false);
		if (newestFirst.size() <= keep) return 0;

		int deleted = 0;
		for (String address : newestFirst.subList(keep, newestFirst.size()))
			if (addresses.get().delete(uniqueId, address)) deleted++;

		return deleted;
	}
}
