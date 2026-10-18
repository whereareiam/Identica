package me.whereareiam.identica.common.identity.address;

import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.AccountAddresses;
import me.whereareiam.identica.model.identity.address.AddressRange;
import me.whereareiam.identica.type.identity.address.AddressStorage;
import me.whereareiam.identica.util.NetworkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Turns an IP address into the forms {@code identity/addresses.yml} stores: the address and its group, written out
 * or hashed with the document's key. One encoder belongs to one version of the document.
 */
final class AddressEncoder {
	private static final String HASH = "HmacSHA256";

	private final @NotNull AccountAddresses document;
	private final @NotNull List<AddressRange> ignored;
	private final @Nullable SecretKeySpec key;

	AddressEncoder(@NotNull AccountAddresses document) {
		this.document = document;
		this.ignored = ignoredRanges(document.getIgnored());
		this.key = document.getSecret().isBlank() ? null : new SecretKeySpec(document.getSecret().getBytes(StandardCharsets.UTF_8), HASH);
		if (document.getStorage() == AddressStorage.HASHED && key == null)
			Logger.warn("identity/addresses.yml stores addresses hashed but has no secret; no address is recorded until it has one");
	}

	@NotNull AccountAddresses document() {
		return document;
	}

	/**
	 * Returns the stored forms of an address, or {@code null} when it is not an IP address, is ignored, or the
	 * addresses are hashed without a key.
	 */
	@Nullable Encoded encode(@Nullable String ip) {
		InetAddress address = NetworkUtil.parseAddress(ip);
		if (address == null || isIgnored(address)) return null;

		return encode(address);
	}

	/**
	 * Returns the stored forms of an address that is not ignored, or {@code null} when the addresses are hashed
	 * without a key.
	 */
	@Nullable Encoded encode(@NotNull InetAddress address) {
		String plainAddress = NetworkUtil.formatAddress(address);
		String plainNetwork = AddressRange.of(address, prefixLength(address)).toString();
		if (document.getStorage() == AddressStorage.PLAIN) return new Encoded(plainAddress, plainNetwork);
		if (key == null) return null;

		return new Encoded(hash(plainAddress), hash(plainNetwork));
	}

	boolean isIgnored(@NotNull InetAddress address) {
		for (AddressRange range : ignored)
			if (range.contains(address)) return true;

		return false;
	}

	private int prefixLength(@NotNull InetAddress address) {
		AccountAddresses.Grouping grouping = document.getGrouping();

		return address instanceof Inet4Address ? grouping.getIpv4() : grouping.getIpv6();
	}

	private @NotNull String hash(@NotNull String value) {
		try {
			Mac mac = Mac.getInstance(HASH);
			mac.init(key);

			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Every Java runtime supports " + HASH, e);
		}
	}

	private static @NotNull List<AddressRange> ignoredRanges(@NotNull List<String> values) {
		List<AddressRange> ranges = new ArrayList<>();
		for (String value : values) {
			AddressRange range = AddressRange.parse(value).orElse(null);
			if (range == null) {
				Logger.warn("identity/addresses.yml ignores \"%s\", which is not an address or range; it is skipped", value);
				continue;
			}
			ranges.add(range);
		}

		return List.copyOf(ranges);
	}

	/**
	 * The stored forms of one address and its group.
	 */
	record Encoded(@NotNull String address, @NotNull String network) {
	}
}
