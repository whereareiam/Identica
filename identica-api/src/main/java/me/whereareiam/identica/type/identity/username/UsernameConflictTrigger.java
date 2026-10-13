package me.whereareiam.identica.type.identity.username;

/**
 * What brings a username conflict up.
 */
public enum UsernameConflictTrigger {
	/**
	 * A player joins through a provider for the first time and wants its name: a new account, or a provider newly
	 * becoming the one an existing account takes its name from.
	 */
	FIRST_JOIN,

	/**
	 * The provider of a known account gives it a new name, such as after a rename at Mojang.
	 */
	PROVIDER_RENAME
}
