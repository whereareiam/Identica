package me.whereareiam.identica.addon.dialogs.type;

/**
 * What a dialog button does.
 */
public enum ActionType {
	/**
	 * Runs its command as the player, with each {@code $(key)} replaced by the value of that input.
	 */
	COMMAND,
	/**
	 * Asks the player to open its URL.
	 */
	URL,
	/**
	 * Copies its text to the clipboard.
	 */
	COPY,
	/**
	 * Only closes the dialog.
	 */
	CLOSE,
	/**
	 * Closes the dialog and switches the player to chat: the addon shows them no more dialogs until they reconnect,
	 * and they answer Identica's chat prompts.
	 */
	CHAT,
	/**
	 * Disconnects the player from the proxy, with its text as the reason their client shows.
	 */
	DISCONNECT
}
