package me.whereareiam.identica.common.identity.address;

import com.google.inject.util.Providers;
import me.whereareiam.identica.model.config.AccountAddresses;
import me.whereareiam.identica.model.identity.address.AccountAddress;
import me.whereareiam.identica.persistence.address.AccountAddressPersistenceService;
import me.whereareiam.identica.type.identity.address.AddressStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Account Address Maintenance")
class AccountAddressMaintenanceTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final long NOW = 10_000_000_000L;

	private final AccountAddressPersistenceService addresses = mock(AccountAddressPersistenceService.class);
	private final AccountAddresses document = new AccountAddresses();
	private final AccountAddressMaintenance maintenance = new AccountAddressMaintenance(
			new DefaultAccountAddressService(Providers.of(document), addresses, () -> NOW),
			addresses,
			() -> NOW
	);

	@Test
	@DisplayName("Regroups the addresses stored as they are after the grouping changed")
	void regroupsPlainAddresses() {
		document.getGrouping().setIpv4(24);
		AccountAddress stored = address(ALICE, "203.0.113.7", "203.0.113.7/32", true);
		when(addresses.findAll()).thenReturn(List.of(stored, address(BOB, "203.0.113.9", "203.0.113.0/24", false)));

		maintenance.run();

		ArgumentCaptor<AccountAddress> replaced = ArgumentCaptor.forClass(AccountAddress.class);
		verify(addresses).replace(eq(ALICE), eq("203.0.113.7"), replaced.capture());
		assertEquals("203.0.113.0/24", replaced.getValue().getNetwork());
		assertTrue(replaced.getValue().isRegistration());
		verify(addresses, never()).replace(eq(BOB), anyString(), any());
	}

	@Test
	@DisplayName("Hashes the addresses stored as they are once addresses are hashed, and leaves hashes alone")
	void hashesPlainAddresses() {
		document.setStorage(AddressStorage.HASHED);
		document.setSecret("secret");
		String hash = "a".repeat(64);
		when(addresses.findAll()).thenReturn(List.of(address(ALICE, "203.0.113.7", "203.0.113.7/32", false), address(BOB, hash, hash, false)));

		maintenance.run();

		ArgumentCaptor<AccountAddress> replaced = ArgumentCaptor.forClass(AccountAddress.class);
		verify(addresses).replace(eq(ALICE), eq("203.0.113.7"), replaced.capture());
		assertEquals(64, replaced.getValue().getAddress().length());
		verify(addresses, never()).replace(eq(BOB), anyString(), any());
	}

	@Test
	@DisplayName("Deletes the addresses that became ignored")
	void deletesIgnoredAddresses() {
		document.setIgnored(List.of("203.0.113.0/24"));
		when(addresses.findAll()).thenReturn(List.of(address(ALICE, "203.0.113.7", "203.0.113.7/32", true)));

		maintenance.run();

		verify(addresses).delete(ALICE, "203.0.113.7");
	}

	@Test
	@DisplayName("Removes idle addresses and lets registration addresses expire only when a retention is set")
	void appliesTheRetention() {
		when(addresses.findAll()).thenReturn(List.of());

		maintenance.run();
		verify(addresses).deleteIdle(NOW - Duration.ofDays(90).toMillis());
		verify(addresses, never()).expireRegistrations(anyLong());

		document.getRetention().setRegistration(Duration.ofDays(30));
		document.getRetention().setIdle(Duration.ZERO);
		maintenance.run();
		verify(addresses).expireRegistrations(NOW - Duration.ofDays(30).toMillis());
	}

	@Test
	@DisplayName("Trims the accounts that keep more addresses than allowed, not counting their registration address")
	void trimsAccountsBeyondTheLimit() {
		document.getRetention().setMaxPerAccount(1);
		when(addresses.findAll()).thenReturn(List.of(
				address(ALICE, "198.51.100.1", "198.51.100.1/32", true),
				address(ALICE, "198.51.100.2", "198.51.100.2/32", false),
				address(BOB, "198.51.100.3", "198.51.100.3/32", false),
				address(BOB, "198.51.100.4", "198.51.100.4/32", false)
		));

		maintenance.run();

		verify(addresses).trim(BOB, 1);
		verify(addresses, never()).trim(eq(ALICE), any(Integer.class));
	}

	private static AccountAddress address(UUID uniqueId, String address, String network, boolean registration) {
		return AccountAddress.builder()
				.uniqueId(uniqueId)
				.address(address)
				.network(network)
				.registration(registration)
				.firstSeenAt(1)
				.lastSeenAt(1)
				.build();
	}
}
