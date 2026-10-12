package me.whereareiam.identica.addon.dialogs.type;

/**
 * What the client does with a dialog after one of its buttons ran its action.
 */
public enum AfterAction {
	/**
	 * Closes the dialog.
	 */
	CLOSE,
	/**
	 * Keeps the dialog open until the addon replaces or closes it, such as after a wrong password.
	 */
	NONE,
	/**
	 * Shows a waiting screen until the server shows another dialog.
	 */
	WAIT_FOR_RESPONSE
}
