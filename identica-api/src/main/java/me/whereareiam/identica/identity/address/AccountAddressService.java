package me.whereareiam.identica.identity.address;

import me.whereareiam.identica.type.identity.address.AddressRecording;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Keeps the IP addresses accounts register and log in from, as {@code identity/addresses.yml} says: which are kept,
 * whether they are stored as they are or hashed, which are never kept, and which addresses count as one. Identica
 * records them itself; other parts read them, such as the sentinel's limit on accounts per address.
 *
 * <pre>{@code
 * List<Long> registrations = addresses.findRegistrationTimes(context.getIp(), Duration.ofDays(30));
 * if (registrations.size() >= 3) refuse();
 * }</pre>
 */
public interface AccountAddressService {
	/**
	 * Returns which addresses are kept.
	 *
	 * @return what {@code identity/addresses.yml} records
	 */
	@NotNull AddressRecording recording();

	/**
	 * Records the address an account was just registered from. Nothing is recorded when registrations are not
	 * recorded, the address is not an IP address, or it is one of the ignored addresses.
	 *
	 * @param uniqueId the account
	 * @param ip       the address
	 */
	void recordRegistration(@NotNull UUID uniqueId, @Nullable String ip);

	/**
	 * Records an address an account just logged in from, and removes its oldest addresses beyond the number each
	 * account keeps. Nothing is recorded when logins are not recorded, the address is not an IP address, or it is
	 * one of the ignored addresses.
	 *
	 * @param uniqueId the account
	 * @param ip       the address
	 */
	void recordConnection(@NotNull UUID uniqueId, @Nullable String ip);

	/**
	 * Finds when each account registered from the group of an address, oldest first; the grouping in
	 * {@code identity/addresses.yml} decides which addresses count as one.
	 *
	 * @param ip     the address
	 * @param window how far back registrations count; {@code null}, zero or negative counts every registration whose
	 *               address is still kept
	 * @return the registration times in epoch milliseconds, one per account; empty when the address is not an IP
	 * address, is ignored, or registrations are not recorded
	 */
	@NotNull List<Long> findRegistrationTimes(@Nullable String ip, @Nullable Duration window);
}
