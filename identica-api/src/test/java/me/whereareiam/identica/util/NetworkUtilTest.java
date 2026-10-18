package me.whereareiam.identica.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Network Util")
class NetworkUtilTest {
	@Test
	@DisplayName("Parses IPv4 and IPv6 literals, with brackets or a scope, and writes them in their shortest form")
	void parsesAddressLiterals() {
		assertEquals("203.0.113.7", NetworkUtil.formatAddress(NetworkUtil.parseAddress(" 203.0.113.7 ")));
		assertEquals("2001:db8::1", NetworkUtil.formatAddress(NetworkUtil.parseAddress("2001:0db8:0:0:0:0:0:1")));
		assertEquals("::1", NetworkUtil.formatAddress(NetworkUtil.parseAddress("[0:0:0:0:0:0:0:1]")));
		assertEquals("fe80::1", NetworkUtil.formatAddress(NetworkUtil.parseAddress("fe80::1%eth0")));
	}

	@Test
	@DisplayName("Turns an IPv6 address that maps an IPv4 address into that IPv4 address")
	void unwrapsMappedAddresses() {
		InetAddress address = NetworkUtil.parseAddress("::ffff:203.0.113.7");

		assertInstanceOf(Inet4Address.class, address);
		assertEquals("203.0.113.7", NetworkUtil.formatAddress(address));
	}

	@Test
	@DisplayName("Never looks up a host name and refuses anything that is not an address")
	void refusesHostNames() {
		assertNull(NetworkUtil.parseAddress("localhost"));
		assertNull(NetworkUtil.parseAddress("example.com"));
		assertNull(NetworkUtil.parseAddress("203.0.113"));
		assertNull(NetworkUtil.parseAddress(""));
		assertNull(NetworkUtil.parseAddress(null));
	}
}
