package me.whereareiam.identica.common.identity.address;

import com.google.inject.util.Providers;
import me.whereareiam.identica.model.config.AccountAddresses;
import me.whereareiam.identica.model.identity.address.AccountAddress;
import me.whereareiam.identica.persistence.address.AccountAddressPersistenceService;
import me.whereareiam.identica.type.identity.address.AddressRecording;
import me.whereareiam.identica.type.identity.address.AddressStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Default Account Address Service")
class DefaultAccountAddressServiceTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final long NOW = 1_000_000L;

	private final AccountAddressPersistenceService addresses = mock(AccountAddressPersistenceService.class);
	private final AccountAddresses document = new AccountAddresses();
	private final DefaultAccountAddressService service = new DefaultAccountAddressService(Providers.of(document), addresses, () -> NOW);

	@Test
	@DisplayName("Records a registration with the address as it is and its group by the IPv6 prefix")
	void recordsARegistration() {
		service.recordRegistration(ALICE, "2001:db8:1:2:aaaa::5");

		AccountAddress recorded = recorded();
		assertEquals(ALICE, recorded.getUniqueId());
		assertEquals("2001:db8:1:2:aaaa::5", recorded.getAddress());
		assertEquals("2001:db8:1:2::/64", recorded.getNetwork());
		assertTrue(recorded.isRegistration());
		assertEquals(NOW, recorded.getFirstSeenAt());
		assertEquals(NOW, recorded.getLastSeenAt());
	}

	@Test
	@DisplayName("Records a login and then keeps the account's newest addresses")
	void recordsALoginAndTrims() {
		service.recordConnection(ALICE, "203.0.113.7");

		AccountAddress recorded = recorded();
		assertEquals("203.0.113.7/32", recorded.getNetwork());
		assertFalse(recorded.isRegistration());
		verify(addresses).trim(ALICE, 20);
	}

	@Test
	@DisplayName("Records no login when only registrations are recorded, and nothing when nothing is")
	void followsWhatIsRecorded() {
		document.setRecord(AddressRecording.REGISTRATION);
		service.recordConnection(ALICE, "203.0.113.7");
		verifyNoInteractions(addresses);

		document.setRecord(AddressRecording.NONE);
		service.recordRegistration(ALICE, "203.0.113.7");
		assertEquals(List.of(), service.findRegistrationTimes("203.0.113.7", null));
		verifyNoInteractions(addresses);
	}

	@Test
	@DisplayName("Records no ignored address and nothing that is not an IP address")
	void skipsIgnoredAddresses() {
		document.setIgnored(List.of("10.0.0.0/8", "not an address"));

		service.recordRegistration(ALICE, "10.1.2.3");
		service.recordRegistration(ALICE, "localhost");
		service.recordConnection(ALICE, null);

		verifyNoInteractions(addresses);
	}

	@Test
	@DisplayName("Hashes the address and its group with the secret, so equal addresses still match")
	void hashesAddresses() {
		document.setStorage(AddressStorage.HASHED);
		document.setSecret("first");
		service.recordRegistration(ALICE, "203.0.113.7");
		AccountAddress first = recorded();

		assertEquals(64, first.getAddress().length());
		assertNotEquals("203.0.113.7", first.getAddress());
		assertNotEquals(first.getAddress(), first.getNetwork());

		AccountAddresses changed = new AccountAddresses();
		changed.setStorage(AddressStorage.HASHED);
		changed.setSecret("second");
		DefaultAccountAddressService other = new DefaultAccountAddressService(Providers.of(changed), addresses, () -> NOW);
		other.findRegistrationTimes("203.0.113.7", null);

		ArgumentCaptor<String> network = ArgumentCaptor.forClass(String.class);
		verify(addresses).findRegistrationTimes(network.capture(), any(Long.class));
		assertNotEquals(first.getNetwork(), network.getValue());
	}

	@Test
	@DisplayName("Records nothing hashed without a secret")
	void needsASecretToHash() {
		document.setStorage(AddressStorage.HASHED);
		document.setSecret(" ");

		service.recordRegistration(ALICE, "203.0.113.7");

		verifyNoInteractions(addresses);
	}

	@Test
	@DisplayName("Counts the registrations of the address's group within the window, or all of them without one")
	void countsWithinTheWindow() {
		when(addresses.findRegistrationTimes(anyString(), anyLong())).thenReturn(List.of(1L, 2L));

		assertEquals(List.of(1L, 2L), service.findRegistrationTimes("2001:db8:1:2::9", Duration.ofMillis(400)));
		verify(addresses).findRegistrationTimes("2001:db8:1:2::/64", NOW - 400);

		service.findRegistrationTimes("2001:db8:1:2::9", Duration.ZERO);
		verify(addresses).findRegistrationTimes("2001:db8:1:2::/64", 0);
	}

	@Test
	@DisplayName("Keeps every address when an account may keep any number")
	void trimsNothingWithoutALimit() {
		document.getRetention().setMaxPerAccount(0);

		service.recordConnection(ALICE, "203.0.113.7");

		verify(addresses, never()).trim(any(), any(Integer.class));
	}

	private AccountAddress recorded() {
		ArgumentCaptor<AccountAddress> captor = ArgumentCaptor.forClass(AccountAddress.class);
		verify(addresses).record(captor.capture());

		return captor.getValue();
	}
}
