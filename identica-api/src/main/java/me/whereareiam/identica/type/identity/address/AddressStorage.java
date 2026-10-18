package me.whereareiam.identica.type.identity.address;

/**
 * How Identica stores the IP addresses it keeps for accounts.
 */
public enum AddressStorage {
	/**
	 * The address as it is, which administrators can read back.
	 */
	PLAIN,

	/**
	 * A keyed hash of the address. Addresses can still be compared and counted, but not read back without the key.
	 */
	HASHED
}
