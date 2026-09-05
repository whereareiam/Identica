package me.whereareiam.identica.trait.authoritative.username.conflict.resolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.conflict.resolver.typed.TypedConflictResolver;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameFormatResolver implements TypedConflictResolver<UsernameFormatResolver.Config> {
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*(?::[a-zA-Z0-9]+)*)}");
	private static final Set<String> PROVIDER_NAME_PLACEHOLDERS = Set.of("incomingProvider", "existingProvider");
	private final ProviderOperations providerOperations;

	@Override
	public @NotNull String getId() {
		return "format_display";
	}

	@Override
	public boolean supports(@NotNull String key) {
		return "username".equalsIgnoreCase(key);
	}

	@Override
	public @NotNull Class<Config> getConfigType() {
		return Config.class;
	}

	@Override
	public @NotNull ConflictResolution resolve(@NotNull ConflictContext context, @NotNull Config config) {
		Config.Format formatConfig = config.getFormat();
		String format = formatConfig.getPattern();
		if (format.isBlank()) return ConflictResolution.allow();

		String requested = context.getAttribute(UsernameConflictSchema.ATTRIBUTE_CANDIDATE_USERNAME);
		if (requested == null || requested.isBlank()) return ConflictResolution.allow();

		String incomingProvider = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_INCOMING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID
		);
		String existingProvider = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_EXISTING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID
		);
		String incomingProviderName = providerOperations.displayProviderName(incomingProvider);
		String existingProviderName = providerOperations.displayProviderName(existingProvider);

		String display = replacePlaceholders(format, replacements(
				requested,
				incomingProvider,
				existingProvider,
				incomingProviderName,
				existingProviderName
		));
		if (formatConfig.isUppercase()) display = display.toUpperCase(Locale.ROOT);
		if (formatConfig.isLowercase()) display = display.toLowerCase(Locale.ROOT);

		Target target = resolveTarget(config.getTarget());
		ConflictResolution resolution = ConflictResolution.allow();
		if (target == Target.INCOMING || target == Target.BOTH)
			resolution = resolution.withEffect(UsernameConflictSchema.EFFECT_INCOMING_EFFECTIVE_USERNAME, display);
		if (target == Target.EXISTING || target == Target.BOTH)
			resolution = resolution.withEffect(UsernameConflictSchema.EFFECT_EXISTING_EFFECTIVE_USERNAME, display);

		return resolution;
	}

	private @NotNull Target resolveTarget(@Nullable String raw) {
		if (raw == null || raw.isBlank()) return Target.INCOMING;

		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "existing", "playing", "active" -> Target.EXISTING;
			case "both", "all" -> Target.BOTH;
			default -> Target.INCOMING;
		};
	}

	private @NotNull Map<String, String> replacements(
			@NotNull String requested,
			@Nullable String incomingProvider,
			@Nullable String existingProvider,
			@Nullable String incomingProviderName,
			@Nullable String existingProviderName
	) {
		Map<String, String> replacements = new LinkedHashMap<>();
		replacements.put("username", requested);
		replacements.put("requested", requested);
		replacements.put("incomingProvider", incomingProviderName == null ? "" : incomingProviderName);
		replacements.put("existingProvider", existingProviderName == null ? "" : existingProviderName);
		replacements.put("incomingProviderId", incomingProvider == null ? "" : incomingProvider);
		replacements.put("existingProviderId", existingProvider == null ? "" : existingProvider);

		return replacements;
	}

	private String replacePlaceholders(@Nullable String input, @NotNull Map<String, String> replacements) {
		if (input == null || input.isBlank()) return input;

		Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
		StringBuilder buffer = new StringBuilder();
		while (matcher.find()) {
			String replacement = resolvePlaceholder(matcher.group(1), replacements);
			if (replacement == null) continue;
			matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
		}

		matcher.appendTail(buffer);
		return buffer.toString();
	}

	private @Nullable String resolvePlaceholder(@NotNull String token, @NotNull Map<String, String> replacements) {
		String[] parts = token.split(":");
		String name = parts[0];
		if ("random".equals(name)) return randomDigits(resolveRandomDigits(parts));

		String replacement = replacements.get(name);
		if (replacement == null) return null;
		if (!PROVIDER_NAME_PLACEHOLDERS.contains(name)) return replacement;

		return truncate(replacement, parseMaxSymbolCount(parts));
	}

	private int resolveRandomDigits(@NotNull String[] parts) {
		Integer digits = parseMaxSymbolCount(parts);
		return digits == null ? 1 : digits;
	}

	private @Nullable Integer parseMaxSymbolCount(@NotNull String[] parts) {
		for (String modifier : Arrays.copyOfRange(parts, 1, parts.length)) {
			if (!isDigits(modifier)) continue;

			int parsed = Integer.parseInt(modifier);
			if (parsed < 1) return 1;
			return Math.min(parsed, 10);
		}

		return null;
	}

	private boolean isDigits(@Nullable String value) {
		if (value == null || value.isBlank()) return false;
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) return false;
		}
		return true;
	}

	private @NotNull String truncate(@NotNull String value, @Nullable Integer maxSymbolCount) {
		if (maxSymbolCount == null || value.isEmpty()) return value;

		int codePointCount = value.codePointCount(0, value.length());
		if (codePointCount <= maxSymbolCount)
			return value;

		return value.substring(0, value.offsetByCodePoints(0, maxSymbolCount));
	}

	private String randomDigits(int digits) {
		StringBuilder out = new StringBuilder(digits);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < digits; i++)
			out.append(random.nextInt(10));

		return out.toString();
	}

	private enum Target {
		INCOMING,
		EXISTING,
		BOTH
	}

	@Getter
	@Setter
	public static class Config {
		private @NotNull Format format = new Format();
		private @NotNull String target = "joiner";

		@Getter
		@Setter
		public static class Format {
			private @NotNull String pattern = "";
			private boolean uppercase;
			private boolean lowercase;
		}
	}
}
