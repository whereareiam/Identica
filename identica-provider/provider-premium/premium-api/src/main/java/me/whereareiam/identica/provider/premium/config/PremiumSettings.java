package me.whereareiam.identica.provider.premium.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Configuration model for the premium eligibility.
 */
@Getter
@Setter
@ToString
public class PremiumSettings {
	private @NotNull Lookup lookup = new Lookup();
	private @NotNull Replication replication = new Replication();
	/**
	 * Time-to-live for premium profile observations.
	 */
	private @NotNull Duration profileSnapshotTtl = Duration.ofMinutes(10);

	/**
	 * Lookup configuration for premium resolver checks.
	 */
	@Getter
	@Setter
	@ToString
	public static class Lookup {
		/**
		 * Profile lookup endpoint that accepts {@code {username}} or {@code %s}.
		 */
		private @NotNull String profileEndpoint = "https://api.mojang.com/users/profiles/minecraft/%s";
		/**
		 * Timeout for the resolver lookup request.
		 */
		private @NotNull Duration timeout = Duration.ofSeconds(3);
		/**
		 * Cache time-to-live for resolver lookup results.
		 */
		private @NotNull Duration cacheTtl = Duration.ofMinutes(5);
	}

	/**
	 * Replication configuration for premium provider caches.
	 */
	@Getter
	@Setter
	@ToString
	public static class Replication {
		private @NotNull Cache cache = new Cache();
	}

	/**
	 * Cache namespaces used by the premium provider.
	 */
	@Getter
	@Setter
	@ToString
	public static class Cache {
		private @NotNull String profile = "premium-resolver";
		private @NotNull String profileSnapshot = "premium-profile-snapshot";
	}
}
