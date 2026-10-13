package me.whereareiam.identica.persistence;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for durable account UUID reservations. The account registered next under a reserved username receives the
 * reserved UUID; reservations of usernames are stored under the key {@link #usernameKey(String)} builds.
 */
@SuppressWarnings("unused")
public interface AccountReservationPersistenceService {
	/**
	 * Builds the reservation key of a username. Usernames are compared without regard to case.
	 *
	 * @param username username to reserve an account UUID for
	 * @return reservation key, or {@code null} when the username is blank
	 */
	static @Nullable String usernameKey(@Nullable String username) {
		if (username == null || username.isBlank()) return null;

		return "username:" + username.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Find a reserved account UUID by reservation key.
	 *
	 * @param reservationKey reservation key
	 * @return reserved UUID or empty when none exists
	 */
	@NotNull Optional<UUID> find(@NotNull String reservationKey);

	/**
	 * Store or update an account UUID reservation.
	 *
	 * @param reservationKey reservation key
	 * @param uniqueId reserved account UUID
	 * @param createdAt creation timestamp in epoch millis
	 * @param expiresAt expiration timestamp in epoch millis
	 */
	void reserve(@NotNull String reservationKey, @NotNull UUID uniqueId, long createdAt, long expiresAt);

	/**
	 * Delete a reservation by key.
	 *
	 * @param reservationKey reservation key
	 */
	void delete(@NotNull String reservationKey);

	/**
	 * Delete all reservations for an account UUID.
	 *
	 * @param uniqueId account UUID
	 */
	void deleteByUniqueId(@NotNull UUID uniqueId);

	/**
	 * Delete expired reservations.
	 *
	 * @param now current epoch millis
	 */
	void deleteExpired(long now);
}
