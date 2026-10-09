package me.whereareiam.identica.feature.sentinel.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Sentinel feature settings configuration document.
 */
@Getter
@Setter
@ToString
public class SentinelSettings extends ConfigDocument {
	private boolean enabled = true;

	private @NotNull Sentinels sentinels = new Sentinels();
	private @NotNull Replication replication = new Replication();

	/**
	 * Settings for built-in sentinels owned by the sentinel feature.
	 */
	@Getter
	@Setter
	@ToString
	public static class Sentinels {
		/**
		 * Rate limit applied when clients spam pipeline resume or advance requests.
		 */
		private @NotNull SentinelPolicy resumeSpam = new SentinelPolicy();
	}

	/**
	 * Replication settings used by the sentinel feature runtime.
	 */
	@Getter
	@Setter
	@ToString
	public static class Replication {
		/**
		 * Replication cache namespace used to store sentinel counters and lockouts.
		 */
		private @NotNull String state = "identica:sentinels";
	}
}
