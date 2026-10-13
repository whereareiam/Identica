package me.whereareiam.identica.type.identity.username;

/**
 * What happens when a renamed username would be longer than the 16 characters Minecraft allows.
 */
public enum UsernameRenameTooLong {
	/**
	 * The original name is shortened until the renamed one fits.
	 */
	SHORTEN,

	/**
	 * The rename fails, and with it the arriving player's join.
	 */
	REFUSE
}
