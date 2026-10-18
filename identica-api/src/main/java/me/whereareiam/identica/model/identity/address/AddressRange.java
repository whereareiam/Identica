package me.whereareiam.identica.model.identity.address;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.whereareiam.identica.util.NetworkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * A range of IP addresses written as an address and a prefix length, such as {@code 10.0.0.0/8} or
 * {@code 2001:db8::/32}. A single address without a prefix is a range of exactly that address.
 *
 * <pre>{@code
 * AddressRange school = AddressRange.parse("203.0.113.0/24").orElseThrow();
 * boolean inside = school.contains(NetworkUtil.parseAddress("203.0.113.7"));
 * }</pre>
 */
@Getter
@EqualsAndHashCode
public final class AddressRange {
	/**
	 * The first address of the range, with every bit past the prefix cleared.
	 */
	private final @NotNull InetAddress base;
	/**
	 * How many leading bits an address shares with {@link #base} to belong to the range.
	 */
	private final int prefixLength;

	private AddressRange(@NotNull InetAddress base, int prefixLength) {
		this.base = base;
		this.prefixLength = prefixLength;
	}

	/**
	 * Creates the range of the addresses that share their first {@code prefixLength} bits with an address.
	 *
	 * @param address      any address of the range
	 * @param prefixLength the number of leading bits; it is clamped to the bit length of the address
	 * @return the range, whose {@link #getBase() base} is the address with the bits past the prefix cleared
	 */
	public static @NotNull AddressRange of(@NotNull InetAddress address, int prefixLength) {
		byte[] bytes = address.getAddress();
		int bits = bytes.length * Byte.SIZE;
		int prefix = Math.clamp(prefixLength, 0, bits);
		for (int bit = prefix; bit < bits; bit++)
			bytes[bit / Byte.SIZE] &= (byte) ~(0x80 >>> (bit % Byte.SIZE));

		try {
			return new AddressRange(InetAddress.getByAddress(bytes), prefix);
		} catch (UnknownHostException e) {
			throw new IllegalStateException("An address of " + bytes.length + " bytes is always valid", e);
		}
	}

	/**
	 * Parses a range written as {@code address/prefix} or as a single address. Host names are not looked up.
	 *
	 * @param value the written range
	 * @return the range, or empty when the value is not an address or its prefix is not a number within the
	 * address's bit length
	 */
	public static @NotNull Optional<AddressRange> parse(@Nullable String value) {
		if (value == null || value.isBlank()) return Optional.empty();

		String trimmed = value.trim();
		int slash = trimmed.indexOf('/');
		InetAddress address = NetworkUtil.parseAddress(slash < 0 ? trimmed : trimmed.substring(0, slash));
		if (address == null) return Optional.empty();

		int bits = address.getAddress().length * Byte.SIZE;
		if (slash < 0) return Optional.of(of(address, bits));

		Integer prefix = parsePrefix(trimmed.substring(slash + 1));
		if (prefix == null || prefix < 0 || prefix > bits) return Optional.empty();

		return Optional.of(of(address, prefix));
	}

	/**
	 * Returns whether an address belongs to this range. An IPv4 address never belongs to an IPv6 range, nor the
	 * other way round.
	 *
	 * @param address the address, or {@code null}
	 * @return {@code true} when the address shares this range's prefix
	 */
	public boolean contains(@Nullable InetAddress address) {
		if (address == null) return false;
		if (address.getAddress().length != base.getAddress().length) return false;

		return base.equals(of(address, prefixLength).base);
	}

	/**
	 * Writes the range as {@code address/prefix}, with the address in its shortest form.
	 *
	 * @return the written range
	 */
	@Override
	public @NotNull String toString() {
		return NetworkUtil.formatAddress(base) + "/" + prefixLength;
	}

	private static @Nullable Integer parsePrefix(@NotNull String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
