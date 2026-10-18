package me.whereareiam.identica.persistence.address;

import me.whereareiam.identica.model.identity.address.AccountAddress;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Stores the IP addresses accounts registered and logged in from, one row per account and address, deleted with the
 * account. It takes and returns the stored forms of the addresses; turning an address into its stored form is the
 * job of {@link me.whereareiam.identica.identity.address.AccountAddressService}.
 */
public interface AccountAddressPersistenceService {
	/**
	 * Records that an account used an address. An address the account used before keeps when it was first used and
	 * whether the account registered from it, unless the new record is a registration; its group and when it was
	 * last used are updated.
	 *
	 * @param address the account, the address and when it was used
	 */
	void record(@NotNull AccountAddress address);

	/**
	 * Finds when each account registered from a group of addresses, oldest first.
	 *
	 * @param network the stored form of the group
	 * @param since   the earliest registration counted, in epoch milliseconds; {@code 0} counts every one
	 * @return the registration times, one per account
	 */
	@NotNull List<Long> findRegistrationTimes(@NotNull String network, long since);

	/**
	 * Finds every stored address.
	 *
	 * @return the addresses of every account
	 */
	@NotNull List<AccountAddress> findAll();

	/**
	 * Replaces the stored forms of one address of an account, for example after the grouping or the storage
	 * changed. Nothing changes when the account has no such address, or when it already has the new one.
	 *
	 * @param uniqueId the account
	 * @param address  the stored form of the address to replace
	 * @param stored   the new stored forms of the address and its group
	 */
	void replace(@NotNull UUID uniqueId, @NotNull String address, @NotNull AccountAddress stored);

	/**
	 * Deletes one address of an account, for example one that became ignored.
	 *
	 * @param uniqueId the account
	 * @param address  the stored form of the address
	 */
	void delete(@NotNull UUID uniqueId, @NotNull String address);

	/**
	 * Deletes the addresses accounts did not register from and have not used since a time.
	 *
	 * @param lastSeenBefore the time, in epoch milliseconds
	 * @return how many addresses were deleted
	 */
	int deleteIdle(long lastSeenBefore);

	/**
	 * Stops counting addresses as registration addresses once their account registered before a time; they are
	 * kept and expire like any other address.
	 *
	 * @param registeredBefore the time, in epoch milliseconds
	 * @return how many addresses stopped counting
	 */
	int expireRegistrations(long registeredBefore);

	/**
	 * Deletes the oldest addresses of an account beyond a number, by when they were last used. The address the
	 * account registered from is never deleted and does not count.
	 *
	 * @param uniqueId the account
	 * @param keep     how many of its other addresses are kept
	 * @return how many addresses were deleted
	 */
	int trim(@NotNull UUID uniqueId, int keep);
}
