package me.whereareiam.identica.type.identity.username;

/**
 * What happens when another account holds a renamed username already.
 */
public enum UsernameRenameTaken {
	/**
	 * A number from 2 to 9 is added to the renamed name; when all of them are held, the rename fails.
	 */
	NUMBER,

	/**
	 * The rename fails, and with it the arriving player's join.
	 */
	REFUSE
}
