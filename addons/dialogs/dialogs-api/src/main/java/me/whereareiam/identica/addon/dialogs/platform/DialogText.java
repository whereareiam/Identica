package me.whereareiam.identica.addon.dialogs.platform;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the text of a dialog for the player it is shown to: MiniMessage with Identica's placeholders, such as
 * {@code {prefix}}, and the dialog's own.
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
}
