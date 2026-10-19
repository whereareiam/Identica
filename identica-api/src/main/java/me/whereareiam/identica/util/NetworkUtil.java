package me.whereareiam.identica.util;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.IDN;
import java.net.InetAddress;
import java.util.Locale;

/**
 * Networking-related helpers for host and entrypoint parsing.
 */
public final class NetworkUtil {
	/**
	 * Parses a host or host:port entrypoint string.
	 *
	 * @param raw raw entrypoint string
	 * @return parsed host/port or {@code null} when invalid
	 */
	public static @Nullable ParsedHost parse(@Nullable String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim();
		if (trimmed.isEmpty()) return null;

		HostAndPort parsed;
		try {
			parsed = HostAndPort.fromString(trimmed);
		} catch (IllegalArgumentException ignored) {
			return null;
		}

		String normalizedHost = normalizeHost(parsed.getHost());
		if (normalizedHost == null) return null;

		Integer port = parsed.hasPort() ? parsed.getPort() : null;
		return new ParsedHost(normalizedHost, port);
	}

	/**
	 * Normalizes a host name by applying IDN conversion and lowercasing.
	 *
	 * @param host input host name
	 * @return normalized host or {@code null} when invalid
	 */
	public static @Nullable String normalizeHost(@Nullable String host) {
		if (host == null) return null;
		String trimmed = host.trim();
		if (trimmed.isEmpty()) return null;

		String normalized = trimmed;
		if (!isIpv6Literal(trimmed)) {
			try {
				normalized = IDN.toASCII(trimmed);
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}

		return normalized.toLowerCase(Locale.ROOT);
	}

	/**
	 * Parses an IP address written as an IPv4 or IPv6 literal. Host names are never looked up, the scope of an IPv6
	 * address is dropped, and an IPv6 address that maps an IPv4 address becomes that IPv4 address.
	 *
	 * @param value the written address, with or without the brackets of an IPv6 literal
	 * @return the address, or {@code null} when the value is not an IP literal
	 */
	public static @Nullable InetAddress parseAddress(@Nullable String value) {
		if (value == null) return null;

		String literal = value.trim();
		if (literal.startsWith("[") && literal.endsWith("]")) literal = literal.substring(1, literal.length() - 1);

		int scope = literal.indexOf('%');
		if (scope >= 0) literal = literal.substring(0, scope);
		if (!InetAddresses.isInetAddress(literal)) return null;

		return InetAddresses.forString(literal);
	}

	/**
	 * Writes an IP address in its shortest form, such as {@code 2001:db8::1}, without a scope.
	 *
	 * @param address the address
	 * @return the written address
	 */
	public static @NotNull String formatAddress(@NotNull InetAddress address) {
		return InetAddresses.toAddrString(address);
	}

	private static boolean isIpv6Literal(@NotNull String host) {
		return host.indexOf(':') >= 0;
	}

	/**
	 * Parsed host entrypoint value.
	 *
	 * @param host normalized host
	 * @param port port when specified, otherwise {@code null}
	 */
	public record ParsedHost(@NotNull String host, @Nullable Integer port) {
	}
}
