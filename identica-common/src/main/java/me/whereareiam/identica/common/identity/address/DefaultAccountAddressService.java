package me.whereareiam.identica.common.identity.address;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.identity.address.AccountAddressService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.AccountAddresses;
import me.whereareiam.identica.model.identity.address.AccountAddress;
import me.whereareiam.identica.persistence.address.AccountAddressPersistenceService;
import me.whereareiam.identica.type.identity.address.AddressRecording;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Records and counts addresses as {@code identity/addresses.yml} says. A failing database is logged and leaves the
 * player's login alone: an address that could not be recorded is only missing from the counts.
 */
@Singleton
public class DefaultAccountAddressService implements AccountAddressService {
	private final Provider<AccountAddresses> documentProvider;
	private final AccountAddressPersistenceService addresses;
	private final LongSupplier clock;
	private volatile @Nullable AddressEncoder encoder;

	@Inject
	public DefaultAccountAddressService(
			@NotNull Provider<AccountAddresses> documentProvider,
			@NotNull AccountAddressPersistenceService addresses
	) {
		this(documentProvider, addresses, System::currentTimeMillis);
	}

	DefaultAccountAddressService(
			@NotNull Provider<AccountAddresses> documentProvider,
			@NotNull AccountAddressPersistenceService addresses,
			@NotNull LongSupplier clock
	) {
		this.documentProvider = documentProvider;
		this.addresses = addresses;
		this.clock = clock;
	}

	@Override
	public @NotNull AddressRecording recording() {
		return documentProvider.get().getRecord();
	}

	@Override
	public void recordRegistration(@NotNull UUID uniqueId, @Nullable String ip) {
		AddressEncoder encoder = encoder();
		if (!encoder.document().getRecord().recordsRegistrations()) return;

		record(encoder, uniqueId, ip, true);
	}

	@Override
	public void recordConnection(@NotNull UUID uniqueId, @Nullable String ip) {
		AddressEncoder encoder = encoder();
		if (!encoder.document().getRecord().recordsConnections()) return;
		if (!record(encoder, uniqueId, ip, false)) return;

		int keep = encoder.document().getRetention().getMaxPerAccount();
		if (keep <= 0) return;

		try {
			addresses.trim(uniqueId, keep);
		} catch (RuntimeException e) {
			Logger.warn(e, "Failed to remove the oldest addresses of %s", uniqueId);
		}
	}

	@Override
	public @NotNull List<Long> findRegistrationTimes(@Nullable String ip, @Nullable Duration window) {
		AddressEncoder encoder = encoder();
		if (!encoder.document().getRecord().recordsRegistrations()) return List.of();

		AddressEncoder.Encoded encoded = encoder.encode(ip);
		if (encoded == null) return List.of();

		long since = window == null || window.isZero() || window.isNegative() ? 0 : clock.getAsLong() - window.toMillis();
		try {
			return addresses.findRegistrationTimes(encoded.network(), since);
		} catch (RuntimeException e) {
			Logger.warn(e, "Failed to count the registrations from an address");
			return List.of();
		}
	}

	/**
	 * Returns the encoder of the current document, built again after the document was reloaded.
	 */
	@NotNull AddressEncoder encoder() {
		AccountAddresses document = documentProvider.get();
		AddressEncoder current = encoder;
		if (current != null && current.document() == document) return current;

		AddressEncoder rebuilt = new AddressEncoder(document);
		encoder = rebuilt;
		return rebuilt;
	}

	private boolean record(@NotNull AddressEncoder encoder, @NotNull UUID uniqueId, @Nullable String ip, boolean registration) {
		AddressEncoder.Encoded encoded = encoder.encode(ip);
		if (encoded == null) return false;

		long now = clock.getAsLong();
		try {
			addresses.record(AccountAddress.builder()
					.uniqueId(uniqueId)
					.address(encoded.address())
					.network(encoded.network())
					.registration(registration)
					.firstSeenAt(now)
					.lastSeenAt(now)
					.build());
			return true;
		} catch (RuntimeException e) {
			Logger.warn(e, "Failed to record an address of %s", uniqueId);
			return false;
		}
	}
}
