package me.whereareiam.identica.feature.sentinel.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel feature settings configuration document.
 */
@Getter
@Setter
@ToString
public class SentinelSettings {
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
		/**
		 * Rate limit applied to an address whose connections are refused because other connections log in under
		 * their usernames.
		 */
		private @NotNull SentinelPolicy contestedConnection = new SentinelPolicy();
		/**
		 * Limit on how many accounts may be registered from one address.
		 */
		private @NotNull AccountsPerAddress accountsPerAddress = new AccountsPerAddress();
	}

	/**
	 * Settings of the limit on accounts registered from one address. What counts as one address, and how long the
	 * address an account registered from is kept, are set in {@code identity/addresses.yml}.
	 */
	@Getter
	@Setter
	@ToString
	public static class AccountsPerAddress {
		private boolean enabled;
		/**
		 * How many accounts may be registered from one address; reaching it refuses the next registration.
		 */
		private int maxAccounts = 3;
		/**
		 * How far back registrations count; zero counts every account whose registration address is kept.
		 */
		private @NotNull Duration window = Duration.ZERO;
		/**
		 * Addresses and ranges, such as {@code 10.0.0.0/8}, that are never limited.
		 */
		private @NotNull List<String> allowlist = new ArrayList<>();
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
