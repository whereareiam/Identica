package me.whereareiam.identica.addon.dialogs.type;

/**
 * Kind of a dialog input.
 */
public enum InputType {
	/**
	 * A text field.
	 */
	TEXT,
	/**
	 * A checkbox, which puts its {@code onTrue} or {@code onFalse} text into a command.
	 */
	BOOLEAN,
	/**
	 * A button cycling through options, which puts the selected option's ID into a command.
	 */
	SINGLE_OPTION,
	/**
	 * A slider, which puts its number into a command.
	 */
	NUMBER_RANGE
}
