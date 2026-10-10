package me.whereareiam.identica.provider.premium.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration model for the premium provider.
 */
@Getter
@Setter
@ToString
public class PremiumSettings {
	private @NotNull Detection detection = new Detection();
	/**
	 * Time-to-live for premium profile observations.
	 */
	private @NotNull Duration profileSnapshotTtl = Duration.ofMinutes(10);
	private @NotNull Replication replication = new Replication();

	/**
	 * How a joining player with a premium username is recognized as the owner of that premium account.
	 */
	@Getter
	@Setter
	@ToString
	public static class Detection {
		/**
		 * Detection methods, tried in order until one of them decides. A player no method decides for is treated
		 * as not premium.
		 */
		private @NotNull List<PremiumDetectionMethod> methods = new ArrayList<>(List.of(PremiumDetectionMethod.LOOKUP));
		/**
		 * Whether {@link PremiumDetectionMethod#CLIENT_PROFILE} looks a profile up again without the cache before it
		 * treats a client with another profile as not premium.
		 */
		private boolean recheckOnMismatch = false;
		private @NotNull Lookup lookup = new Lookup();
	}

	/**
	 * Profile lookup that tells whether a username belongs to a premium account and which profile it has.
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
		 * Timeout for the lookup request.
		 */
		private @NotNull Duration timeout = Duration.ofSeconds(3);
		/**
		 * Cache time-to-live for lookup results.
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
		private @NotNull String profile = "premium-profile";
		private @NotNull String profileSnapshot = "premium-profile-snapshot";
	}
}
