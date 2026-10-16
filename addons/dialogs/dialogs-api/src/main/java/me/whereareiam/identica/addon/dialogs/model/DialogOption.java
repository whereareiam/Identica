package me.whereareiam.identica.addon.dialogs.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One option of a {@link me.whereareiam.identica.addon.dialogs.type.InputType#SINGLE_OPTION} input.
 */
@Value
public class DialogOption {
	/**
	 * What the option puts into a command while it is selected.
	 */
	@NotNull String id;
	/**
	 * MiniMessage text the option is shown with.
	 */
	@NotNull String display;
	/**
	 * Whether the option is selected when the dialog opens.
	 */
	boolean initial;
}
