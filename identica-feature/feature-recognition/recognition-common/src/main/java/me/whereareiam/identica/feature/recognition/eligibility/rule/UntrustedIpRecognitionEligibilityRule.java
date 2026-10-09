package me.whereareiam.identica.feature.recognition.eligibility.rule;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionFeatures;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.eligibility.RecognitionEligibilityRule;
import me.whereareiam.identica.feature.recognition.eligibility.matcher.IpMatcher;
import me.whereareiam.identica.feature.recognition.eligibility.matcher.type.CidrMatcher;
import me.whereareiam.identica.feature.recognition.eligibility.matcher.type.ExactIpMatcher;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityContext;
import me.whereareiam.identica.feature.recognition.model.eligibility.RecognitionEligibilityRuleDecision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UntrustedIpRecognitionEligibilityRule implements RecognitionEligibilityRule {
	private final Provider<RecognitionSettings> settingsProvider;
	private final RecognitionProvidersProvider providersProvider;

	@Override
	public @NotNull String id() {
		return "untrusted-ip";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull RecognitionEligibilityRuleDecision evaluate(@NotNull RecognitionEligibilityContext context) {
		RecognitionSettings.Eligibility.UntrustedIps untrustedIps = settingsProvider.get()
				.getEligibility()
				.getUntrustedIps();

		if (!untrustedIps.isEnabled()) return RecognitionEligibilityRuleDecision.abstain();
		if (allowsRecognitionOverride(context.getProviderId()))
			return RecognitionEligibilityRuleDecision.allow("provider-override-untrusted-ip");

		InetAddress clientAddress = parseClientAddress(context.getClientIp());
		if (clientAddress == null) return RecognitionEligibilityRuleDecision.abstain();

		List<String> entries = untrustedIps.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			IpMatcher matcher = parseEntry(entries.get(i), i);
			if (matcher.matches(clientAddress))
				return RecognitionEligibilityRuleDecision.block("blocked-untrusted-ip");
		}

		return RecognitionEligibilityRuleDecision.abstain();
	}

	private boolean allowsRecognitionOverride(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return false;

		for (Providers.ProviderEntry provider : providersProvider.get().getProviders()) {
			if (provider == null || provider.getId().isBlank()) continue;
			if (!provider.getId().equalsIgnoreCase(providerId)) continue;

			Providers.ProviderEntry.Features features = provider.getFeatures();
			if (!(features instanceof RecognitionFeatures recognitionFeatures))
				return false;

			RecognitionFeatures.Recognition recognition = recognitionFeatures.getRecognition();
			return recognition != null && recognition.isAllowOnUntrustedIps();
		}

		return false;
	}

	private @NotNull IpMatcher parseEntry(@Nullable String entry, int index) {
		if (entry == null || entry.isBlank()) throw invalidEntry(index, entry, "entry must not be blank");

		String trimmed = entry.trim();
		if (trimmed.contains("/")) return parseCidr(trimmed, index);

		InetAddress literal = parseLiteral(trimmed, index, "exact IP");
		return new ExactIpMatcher(literal);
	}

	private @NotNull IpMatcher parseCidr(@NotNull String entry, int index) {
		String[] parts = entry.split("/", -1);
		if (parts.length != 2) throw invalidEntry(index, entry, "CIDR entry must contain exactly one '/' separator");

		InetAddress base = parseLiteral(parts[0], index, "CIDR base");
		int prefix;
		try {
			prefix = Integer.parseInt(parts[1]);
		} catch (NumberFormatException exception) {
			throw invalidEntry(index, entry, "CIDR prefix must be a number");
		}

		int maxPrefix = base.getAddress().length * 8;
		if (prefix < 0 || prefix > maxPrefix)
			throw invalidEntry(index, entry, "CIDR prefix must be between 0 and " + maxPrefix);

		return new CidrMatcher(base, prefix);
	}

	private @NotNull InetAddress parseLiteral(
			@Nullable String value,
			int index,
			@NotNull String label
	) {
		String trimmed = value == null ? "" : value.trim();
		if (!looksLikeIpLiteral(trimmed)) throw invalidEntry(index, value, label + " must be an IP literal");

		try {
			return InetAddress.getByName(trimmed);
		} catch (UnknownHostException exception) {
			throw invalidEntry(index, value, label + " must be a valid IP literal");
		}
	}

	private @Nullable InetAddress parseClientAddress(@Nullable String clientIp) {
		if (clientIp == null || clientIp.isBlank()) return null;

		String trimmed = clientIp.trim();
		if (!looksLikeIpLiteral(trimmed)) return null;

		try {
			return InetAddress.getByName(trimmed);
		} catch (UnknownHostException exception) {
			return null;
		}
	}

	private boolean looksLikeIpLiteral(@NotNull String value) {
		if (value.isBlank()) return false;

		if (value.indexOf(':') >= 0) return value.chars().allMatch(character -> isHexDigit(character)
				|| character == ':'
				|| character == '.');

		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) return false;

		for (String part : parts) {
			if (part.isBlank() || part.length() > 3) return false;
			for (int i = 0; i < part.length(); i++) {
				if (!Character.isDigit(part.charAt(i))) return false;
			}

			int octet = Integer.parseInt(part);
			if (octet < 0 || octet > 255) return false;
		}

		return true;
	}

	private boolean isHexDigit(int character) {
		char lowered = Character.toLowerCase((char) character);
		return (lowered >= '0' && lowered <= '9') || (lowered >= 'a' && lowered <= 'f');
	}

	private @NotNull IllegalStateException invalidEntry(int index, @Nullable String entry, @NotNull String reason) {
		return new IllegalStateException(
				"features.recognition.settings.eligibility.untrustedIps.entries[" + index + "]="
						+ entry
						+ " is invalid: "
						+ reason
		);
	}
}
