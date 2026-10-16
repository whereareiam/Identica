package me.whereareiam.identica.addon.dialogs.model;

import lombok.Value;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import org.jetbrains.annotations.NotNull;

/**
 * A button of a dialog and what it does.
 */
@Value
public class DialogAction {
	/**
	 * MiniMessage label of the button.
	 */
	@NotNull String label;
	@NotNull ActionType type;
	/**
	 * The command without its slash, the URL or the text to copy; empty for {@link ActionType#CLOSE} and
	 * {@link ActionType#CHAT}.
	 */
	@NotNull String value;
}
