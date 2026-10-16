package me.whereareiam.identica.addon.dialogs.model;

import lombok.Value;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
	 * The definition ID of an Identica command for {@link ActionType#COMMAND}, such as {@code login}; the command
	 * line without its slash for {@link ActionType#RUN}; the URL or the text to copy; empty for
	 * {@link ActionType#CLOSE} and {@link ActionType#CHAT}.
	 */
	@NotNull String value;
	/**
	 * The arguments of an Identica command, in order, each {@code $(key)} replaced by the value of that input; empty
	 * for every other type.
	 */
	@NotNull List<String> arguments;
}
