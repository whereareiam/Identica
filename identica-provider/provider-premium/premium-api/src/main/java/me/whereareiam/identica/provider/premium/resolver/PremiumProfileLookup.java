package me.whereareiam.identica.provider.premium.resolver;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up the premium profile of a username and caches the answer when enabled.
 */
@Singleton
public class PremiumProfileLookup {
	private static final Pattern PROFILE_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
	private static final Pattern UNDASHED_ID = Pattern.compile("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})");

	private final @NotNull HttpClient httpClient;
	private final @NotNull Provider<PremiumSettings> settingsProvider;
	private final @NotNull ReplicatedCache<PremiumLookupResult> cache;

	@Inject
	public PremiumProfileLookup(
			@NotNull Provider<PremiumSettings> settingsProvider,
			@NotNull ReplicationSystem replicationSystem
	) {
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();

		this.settingsProvider = settingsProvider;
		ReplicationType<PremiumLookupResult, PremiumLookupResult> type = ReplicationType.identity(PremiumLookupResult.class);
		this.cache = replicationSystem.cache(resolveNamespace(settingsProvider)).replicated(type);
	}

	/**
	 * Resolves whether the given username has a premium profile, using cached results when enabled.
	 *
	 * @param username username to check
	 * @return future that completes with {@code true} when the username has a premium profile; it completes with
	 *         {@code false} when the lookup fails
	 */
	public @NotNull CompletableFuture<Boolean> hasPremiumProfile(@Nullable String username) {
		return findProfileId(username).thenApply(Optional::isPresent);
	}

	/**
	 * Resolves the premium profile id of the given username, using cached results when enabled.
	 *
	 * @param username username to check
	 * @return future that completes with the profile id, or empty when the username has no premium profile or the
	 *         lookup fails
	 */
	public @NotNull CompletableFuture<Optional<UUID>> findProfileId(@Nullable String username) {
		String key = resolveKey(username);
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());
		if (resolveTtlMs() <= 0) return fetchProfileId(username);

		return cache.get(key)
				.thenCompose(cached -> cached
						.map(result -> CompletableFuture.completedFuture(toProfileId(result)))
						.orElseGet(() -> fetchProfileId(username)));
	}

	/**
	 * Looks up the premium profile id of the given username without reading the cache, and caches the answer when
	 * enabled.
	 *
	 * @param username username to check
	 * @return future that completes with the profile id, or empty when the username has no premium profile or the
	 *         lookup fails
	 */
	public @NotNull CompletableFuture<Optional<UUID>> fetchProfileId(@Nullable String username) {
		String key = resolveKey(username);
		if (key == null) return CompletableFuture.completedFuture(Optional.empty());

		PremiumSettings.Lookup lookup = settingsProvider.get().getDetection().getLookup();
		String endpoint = lookup.getProfileEndpoint();
		if (endpoint.isBlank()) return CompletableFuture.completedFuture(Optional.empty());

		String url = resolveUrl(endpoint, username.trim());
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET();

		Duration timeout = lookup.getTimeout();
		if (isUsable(timeout)) builder.timeout(timeout);

		return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
				.thenCompose(response -> {
					// Only a definite answer is cached, so a failed lookup is retried on the next join.
					PremiumLookupResult result = resolveResult(response);
					if (result == null) return CompletableFuture.completedFuture(Optional.<UUID>empty());

					long ttlMs = resolveTtlMs();
					if (ttlMs <= 0) return CompletableFuture.completedFuture(toProfileId(result));

					return cache.put(key, result, ttlMs).thenApply(ignored -> toProfileId(result));
				})
				.exceptionally(ignored -> Optional.empty());
	}

	/**
	 * Returns the answer of a response, or {@code null} when the response gives none and must not be cached.
	 */
	private @Nullable PremiumLookupResult resolveResult(@NotNull HttpResponse<String> response) {
		int status = response.statusCode();
		if (status == 204 || status == 404) return new PremiumLookupResult(null);
		if (status != 200) return null;

		Matcher matcher = PROFILE_ID.matcher(response.body() == null ? "" : response.body());
		if (!matcher.find()) return null;

		return new PremiumLookupResult(UNDASHED_ID.matcher(matcher.group(1)).replaceFirst("$1-$2-$3-$4-$5"));
	}

	private @NotNull Optional<UUID> toProfileId(@NotNull PremiumLookupResult result) {
		String profileId = result.getProfileId();
		if (profileId == null || profileId.isBlank()) return Optional.empty();

		try {
			return Optional.of(UUID.fromString(profileId));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	private @Nullable String resolveKey(@Nullable String username) {
		if (username == null || username.isBlank()) return null;
		return username.trim().toLowerCase(Locale.ROOT);
	}

	private String resolveUrl(String endpoint, String username) {
		String encoded = encode(username);
		if (endpoint.contains("%s"))
			return String.format(endpoint, encoded);

		return endpoint.replace("{username}", encoded);
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private boolean isUsable(@Nullable Duration duration) {
		return duration != null
				&& !duration.isZero()
				&& !duration.isNegative();
	}

	private long resolveTtlMs() {
		Duration duration = settingsProvider.get().getDetection().getLookup().getCacheTtl();
		if (!isUsable(duration)) return 0;
		return duration.toMillis();
	}

	private static String resolveNamespace(Provider<PremiumSettings> settingsProvider) {
		String namespace = settingsProvider.get().getReplication().getCache().getProfile();
		if (namespace.isBlank()) throw new IllegalStateException("premium.settings.replication.cache.profile is missing");

		return namespace;
	}
}
