package me.whereareiam.identica.feature.recognition.eligibility.matcher.type;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.recognition.eligibility.matcher.IpMatcher;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;

@RequiredArgsConstructor
public final class ExactIpMatcher implements IpMatcher {
	private final @NotNull InetAddress address;

	@Override
	public boolean matches(@NotNull InetAddress value) {
		return address.equals(value);
	}
}
