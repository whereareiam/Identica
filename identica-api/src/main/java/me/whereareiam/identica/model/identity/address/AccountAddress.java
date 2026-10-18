package me.whereareiam.identica.model.identity.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An IP address an account registered or logged in from, as Identica stores it: the address and its group, each
 * either written out or hashed, depending on {@code identity/addresses.yml}.
 */
@Getter
@ToString
@AllArgsConstructor
@Builder(toBuilder = true)
public class AccountAddress {
	/**
	 * The account.
	 */
	private final @NotNull UUID uniqueId;
	/**
	 * The stored form of the address: the address itself, such as {@code 203.0.113.7}, or its hash.
	 */
	private final @NotNull String address;
	/**
	 * The stored form of the group of addresses that count as one, such as {@code 2001:db8:1:2::/64}, or its hash.
	 */
	private final @NotNull String network;
	/**
	 * Whether the account registered from this address.
	 */
	private final boolean registration;
	/**
	 * When the account first used this address, in epoch milliseconds.
	 */
	private final long firstSeenAt;
	/**
	 * When the account last used this address, in epoch milliseconds.
	 */
	private final long lastSeenAt;
}
