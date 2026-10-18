package me.whereareiam.identica.common.identity.address;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.AccountAddresses;
import me.whereareiam.identica.model.identity.address.AccountAddress;
import me.whereareiam.identica.persistence.address.AccountAddressPersistenceService;
import me.whereareiam.identica.util.NetworkUtil;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Brings the stored addresses in line with {@code identity/addresses.yml} and removes the ones it no longer keeps.
 * Addresses stored as they are take the current grouping and storage, so switching to hashed storage hashes them;
 * hashed addresses cannot be read back and keep the group they were stored with. Every proxy of a shared database
 * may run it at once, since each step leaves the same result however often it runs.
 */
@Singleton
public class AccountAddressMaintenance {
	private final DefaultAccountAddressService service;
	private final AccountAddressPersistenceService addresses;
	private final LongSupplier clock;

	@Inject
	public AccountAddressMaintenance(
			@NotNull DefaultAccountAddressService service,
			@NotNull AccountAddressPersistenceService addresses
	) {
		this(service, addresses, System::currentTimeMillis);
	}

	AccountAddressMaintenance(
			@NotNull DefaultAccountAddressService service,
			@NotNull AccountAddressPersistenceService addresses,
			@NotNull LongSupplier clock
	) {
		this.service = service;
		this.addresses = addresses;
		this.clock = clock;
	}

	/**
	 * Runs every step once, logging a failure instead of passing it on.
	 */
	public void run() {
		try {
			AddressEncoder encoder = service.encoder();
			List<AccountAddress> stored = addresses.findAll();
			int changed = reencode(encoder, stored);
			int removed = expire(encoder.document().getRetention()) + trim(encoder.document(), stored);
			if (changed > 0 || removed > 0)
				Logger.debug("Updated %d stored addresses and removed %d", changed, removed);
		} catch (RuntimeException e) {
			Logger.warn(e, "Failed to clean up the stored addresses of accounts");
		}
	}

	/**
	 * Deletes the addresses that became ignored and stores the others in their current form. Returns how many
	 * changed.
	 */
	private int reencode(@NotNull AddressEncoder encoder, @NotNull List<AccountAddress> stored) {
		int changed = 0;
		for (AccountAddress row : stored) {
			InetAddress address = NetworkUtil.parseAddress(row.getAddress());
			if (address == null) continue;

			if (encoder.isIgnored(address)) {
				addresses.delete(row.getUniqueId(), row.getAddress());
				changed++;
				continue;
			}

			AddressEncoder.Encoded encoded = encoder.encode(address);
			if (encoded == null) continue;
			if (encoded.address().equals(row.getAddress()) && encoded.network().equals(row.getNetwork())) continue;

			addresses.replace(row.getUniqueId(), row.getAddress(), row.toBuilder()
					.address(encoded.address())
					.network(encoded.network())
					.build());
			changed++;
		}

		return changed;
	}

	private int expire(@NotNull AccountAddresses.Retention retention) {
		long now = clock.getAsLong();
		int removed = 0;
		if (isPositive(retention.getRegistration()))
			addresses.expireRegistrations(now - retention.getRegistration().toMillis());
		if (isPositive(retention.getIdle()))
			removed += addresses.deleteIdle(now - retention.getIdle().toMillis());

		return removed;
	}

	/**
	 * Trims the accounts that keep more addresses than {@code retention.maxPerAccount}, which happens after it was
	 * lowered.
	 */
	private int trim(@NotNull AccountAddresses document, @NotNull List<AccountAddress> stored) {
		int keep = document.getRetention().getMaxPerAccount();
		if (keep <= 0) return 0;

		Map<UUID, Integer> counts = new HashMap<>();
		for (AccountAddress row : stored)
			if (!row.isRegistration()) counts.merge(row.getUniqueId(), 1, Integer::sum);

		return counts.entrySet().stream()
				.filter(account -> account.getValue() > keep)
				.map(Map.Entry::getKey)
				.mapToInt(uniqueId -> addresses.trim(uniqueId, keep))
				.sum();
	}

	private static boolean isPositive(@NotNull Duration duration) {
		return !duration.isZero() && !duration.isNegative();
	}
}
