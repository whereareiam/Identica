package me.whereareiam.identica.type.identity.address;

/**
 * Which IP addresses Identica keeps for an account.
 */
public enum AddressRecording {
	/**
	 * The address an account registered from and every address it logs in from.
	 */
	ALL,

	/**
	 * Only the address an account registered from.
	 */
	REGISTRATION,

	/**
	 * No address at all; nothing that counts addresses can work.
	 */
	NONE;

	/**
	 * Returns whether the address an account registers from is kept.
	 *
	 * @return {@code true} unless nothing is recorded
	 */
	public boolean recordsRegistrations() {
		return this != NONE;
	}

	/**
	 * Returns whether the addresses an account logs in from are kept.
	 *
	 * @return {@code true} only when every address is recorded
	 */
	public boolean recordsConnections() {
		return this == ALL;
	}
}
