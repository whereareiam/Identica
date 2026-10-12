package me.whereareiam.identica.adapter.database.account;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountReservationEntity;
import me.whereareiam.identica.adapter.database.repository.account.AccountReservationRepository;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultAccountReservationPersistenceService implements AccountReservationPersistenceService {
	private final Provider<EntityAccess<AccountReservationEntity>> reservations;
	private final Provider<AccountReservationRepository> repository;

	@Inject
	public DefaultAccountReservationPersistenceService(
			Provider<EntityAccess<AccountReservationEntity>> reservations,
			Provider<AccountReservationRepository> repository
	) {
		this.reservations = reservations;
		this.repository = repository;
	}

	@Override
	public @NotNull Optional<UUID> find(@NotNull String reservationKey) {
		if (reservationKey.isBlank()) return Optional.empty();

		try {
			long now = System.currentTimeMillis();
			return reservations.get().find(reservationKey)
					.filter(reservation -> !isExpired(reservation.getExpiresAt(), now))
					.map(AccountReservationEntity::getUniqueId);
		} catch (Exception e) {
			Logger.warn(e, "Failed to load account reservation %s", reservationKey);
			return Optional.empty();
		}
	}

	@Override
	public void reserve(@NotNull String reservationKey, @NotNull UUID uniqueId, long createdAt, long expiresAt) {
		if (reservationKey.isBlank()) return;
		if (expiresAt <= createdAt) return;

		try {
			reservations.get().upsert(new AccountReservationEntity(reservationKey, uniqueId, createdAt, expiresAt));
		} catch (Exception e) {
			Logger.warn(e, "Failed to reserve account UUID %s for %s", uniqueId, reservationKey);
		}
	}

	@Override
	public void delete(@NotNull String reservationKey) {
		if (reservationKey.isBlank()) return;

		try {
			reservations.get().delete(reservationKey);
		} catch (Exception e) {
			Logger.warn(e, "Failed to delete account reservation %s", reservationKey);
		}
	}

	@Override
	public void deleteByUniqueId(@NotNull UUID uniqueId) {
		try {
			repository.get().deleteByUniqueId(uniqueId);
		} catch (Exception e) {
			Logger.warn(e, "Failed to delete account reservations for %s", uniqueId);
		}
	}

	@Override
	public void deleteExpired(long now) {
		try {
			repository.get().deleteExpired(now);
		} catch (Exception e) {
			Logger.warn(e, "Failed to delete expired account reservations");
		}
	}

	private boolean isExpired(long expiresAt, long now) {
		if (expiresAt > now) return false;
		repository.get().deleteExpired(now);
		return true;
	}
}
