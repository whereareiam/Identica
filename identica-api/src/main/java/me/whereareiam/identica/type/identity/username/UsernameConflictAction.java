package me.whereareiam.identica.type.identity.username;

/**
 * What a username conflict decision does. None of them lets one account into another.
 */
public enum UsernameConflictAction {
	/**
	 * The arriving player is refused and the holder keeps the name.
	 */
	REFUSE,

	/**
	 * The holder keeps the name and the arriving account gets a renamed one.
	 */
	RENAME_ARRIVING,

	/**
	 * The arriving account gets the name and the holder gets a renamed one.
	 */
	RENAME_HOLDER
}
