package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.type.identity.address.AddressRecording;
import me.whereareiam.identica.type.identity.address.AddressStorage;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The document {@code identity/addresses}: which IP addresses Identica keeps for accounts, how it stores and compares
 * them, and for how long.
 */
@Getter
@Setter
@ToString(exclude = "secret")
public class AccountAddresses {
	/**
	 * Which addresses are kept.
	 */
	private @NotNull AddressRecording record = AddressRecording.ALL;
	/**
	 * Whether addresses are stored as they are or hashed.
	 */
	private @NotNull AddressStorage storage = AddressStorage.PLAIN;
	/**
	 * The key of the hashes when addresses are hashed. Every proxy that shares the database needs the same key.
	 */
	private @NotNull String secret = "";
	/**
	 * Addresses and ranges, such as {@code 10.0.0.0/8}, that are never kept.
	 */
	private @NotNull List<String> ignored = new ArrayList<>();
	private @NotNull Grouping grouping = new Grouping();
	private @NotNull Retention retention = new Retention();

	/**
	 * How many leading bits two addresses share to count as one.
	 */
	@Getter
	@Setter
	@ToString
	public static class Grouping {
		/**
		 * The prefix length for IPv4 addresses, from {@code 0} to {@code 32}.
		 */
		private int ipv4 = 32;
		/**
		 * The prefix length for IPv6 addresses, from {@code 0} to {@code 128}.
		 */
		private int ipv6 = 64;
	}

	/**
	 * How long addresses are kept.
	 */
	@Getter
	@Setter
	@ToString
	public static class Retention {
		/**
		 * How long the address an account registered from counts as such, from the registration; zero keeps it as
		 * long as the account exists. Afterwards it is kept like any other address.
		 */
		private @NotNull Duration registration = Duration.ZERO;
		/**
		 * How long an address is kept after the account last used it; zero keeps it forever.
		 */
		private @NotNull Duration idle = Duration.ofDays(90);
		/**
		 * How many addresses one account keeps besides its registration address; zero keeps them all.
		 */
		private int maxPerAccount = 20;
	}
}
