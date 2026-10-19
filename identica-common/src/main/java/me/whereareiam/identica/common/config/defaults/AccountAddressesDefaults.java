package me.whereareiam.identica.common.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.config.AccountAddresses;
import me.whereareiam.identica.type.identity.address.AddressRecording;
import me.whereareiam.identica.type.identity.address.AddressStorage;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;

/**
 * Keeps every address an account uses, as it is, for 90 days after its last use. The hash key is drawn at random
 * when the document is first written, so no two installations share one by accident.
 */
@Singleton
public class AccountAddressesDefaults implements DefaultsProvider<AccountAddresses> {
	private static final int SECRET_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	@Override
	public AccountAddresses supply(AccountAddresses addresses) {
		addresses.setRecord(AddressRecording.ALL);
		addresses.setStorage(AddressStorage.PLAIN);
		addresses.setSecret(secret());
		addresses.setIgnored(new ArrayList<>());

		AccountAddresses.Grouping grouping = new AccountAddresses.Grouping();
		grouping.setIpv4(32);
		grouping.setIpv6(64);
		addresses.setGrouping(grouping);

		AccountAddresses.Retention retention = new AccountAddresses.Retention();
		retention.setRegistration(Duration.ZERO);
		retention.setIdle(Duration.ofDays(90));
		retention.setMaxPerAccount(20);
		addresses.setRetention(retention);
		return addresses;
	}

	private static String secret() {
		byte[] bytes = new byte[SECRET_BYTES];
		RANDOM.nextBytes(bytes);

		return HexFormat.of().formatHex(bytes);
	}
}
