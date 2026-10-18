package me.whereareiam.identica.model.identity.address;

import me.whereareiam.identica.util.NetworkUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Address Range")
class AddressRangeTest {
	@Test
	@DisplayName("Clears the bits past the prefix and writes the range with its prefix")
	void writesTheRangeFromItsBase() {
		assertEquals("203.0.113.0/24", AddressRange.of(NetworkUtil.parseAddress("203.0.113.7"), 24).toString());
		assertEquals("2001:db8:1:2::/64", AddressRange.of(NetworkUtil.parseAddress("2001:db8:1:2:aaaa::5"), 64).toString());
		assertEquals("203.0.113.7/32", AddressRange.parse("203.0.113.7").orElseThrow().toString());
		assertEquals("10.0.0.0/8", AddressRange.parse("10.1.2.3/8").orElseThrow().toString());
	}

	@Test
	@DisplayName("Contains the addresses that share its prefix, never one of the other IP version")
	void containsTheAddressesOfItsPrefix() {
		AddressRange range = AddressRange.parse("10.0.0.0/8").orElseThrow();

		assertTrue(range.contains(NetworkUtil.parseAddress("10.255.0.1")));
		assertFalse(range.contains(NetworkUtil.parseAddress("11.0.0.1")));
		assertFalse(range.contains(NetworkUtil.parseAddress("::a00:1")));
		assertFalse(range.contains(null));
		assertTrue(AddressRange.parse("::/0").orElseThrow().contains(NetworkUtil.parseAddress("2001:db8::1")));
	}

	@Test
	@DisplayName("Refuses host names, prefixes that are no number and prefixes longer than the address")
	void refusesInvalidRanges() {
		assertEquals(Optional.empty(), AddressRange.parse("localhost"));
		assertEquals(Optional.empty(), AddressRange.parse("10.0.0.0/x"));
		assertEquals(Optional.empty(), AddressRange.parse("10.0.0.0/33"));
		assertEquals(Optional.empty(), AddressRange.parse("2001:db8::/129"));
		assertEquals(Optional.empty(), AddressRange.parse(" "));
	}
}
