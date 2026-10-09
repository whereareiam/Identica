package me.whereareiam.identica.feature.recognition.eligibility.matcher.type;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.recognition.eligibility.matcher.IpMatcher;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Arrays;

@RequiredArgsConstructor
public final class CidrMatcher implements IpMatcher {
	private final @NotNull InetAddress baseAddress;
	private final int prefixLength;

	@Override
	public boolean matches(@NotNull InetAddress address) {
		byte[] left = baseAddress.getAddress();
		byte[] right = address.getAddress();
		if (left.length != right.length) return false;

		int fullBytes = prefixLength / 8;
		int remainingBits = prefixLength % 8;
		if (!Arrays.equals(Arrays.copyOf(left, fullBytes), Arrays.copyOf(right, fullBytes)))
			return false;
		if (remainingBits == 0) return true;

		int mask = -(1 << (8 - remainingBits)) & 0xFF;
		return (left[fullBytes] & mask) == (right[fullBytes] & mask);
	}
}
