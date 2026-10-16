package me.whereareiam.identica.addon.dialogs.type;

/**
 * Layout of a dialog, after the dialog types of Minecraft.
 */
public enum DialogType {
	/**
	 * A message with one button, its first action.
	 */
	NOTICE,
	/**
	 * A question with two buttons: its first action and its exit action.
	 */
	CONFIRMATION,
	/**
	 * Any number of buttons, its actions, and optionally an exit button below them.
	 */
	MULTI_ACTION
}
