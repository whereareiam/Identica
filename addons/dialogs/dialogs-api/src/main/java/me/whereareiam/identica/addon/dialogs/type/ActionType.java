package me.whereareiam.identica.addon.dialogs.type;

/**
 * What a dialog button does.
 */
public enum ActionType {
	/**
	 * Runs an Identica command as the player, named by its definition ID, such as Credential's {@code login}, with
	 * the name an administrator gave the command and the button's arguments, each {@code $(key)} replaced by the value
	 * of that input. It also works on the loading screen, where the proxy runs it for the player.
	 */
	COMMAND,
	/**
	 * Runs its command line as the player, with each {@code $(key)} replaced by the value of that input, such as
	 * another plugin's command. A player on the loading screen cannot run it, so the button is left out there.
	 */
	RUN,
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
	 * and they answer Identica's chat prompts. A player on the loading screen cannot use chat, so the button is left
	 * out there.
	 */
	CHAT
}
