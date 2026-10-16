package me.whereareiam.identica.addon.dialogs.platform;

import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the text of a dialog for the player it is shown to: MiniMessage with Identica's placeholders, such as
 * {@code {prefix}}, and the dialog's own; and the command lines of its buttons.
 */
@FunctionalInterface
public interface DialogText {
	/**
	 * Renders one text of the dialog.
	 *
	 * @param text MiniMessage text with {@code {placeholders}}
	 * @return the rendered component
	 */
	@NotNull Component render(@NotNull String text);

	/**
	 * Returns the command line a button runs, without its slash, with a {@code $(key)} placeholder for each input it
	 * takes. An Identica command is named by its definition, so this text resolves {@link ActionType#COMMAND} to the
	 * name an administrator gave it; this default only knows the lines of {@link ActionType#RUN}.
	 *
	 * @param action the button
	 * @return the command line, or {@code null} when the button runs no command or its command is unavailable
	 */
	default @Nullable String command(@NotNull DialogAction action) {
		return action.getType() == ActionType.RUN ? action.getValue() : null;
	}
}
