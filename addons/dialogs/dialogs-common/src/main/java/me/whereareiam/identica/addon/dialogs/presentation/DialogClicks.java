package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The clicks the addon's buttons send to the proxy, as every platform builds and reads them: their IDs, the values a
 * command or disconnect button carries next to the dialog's inputs, and how a command is filled from them.
 */
public final class DialogClicks {
	/**
	 * Prefix of every click ID of the addon; a platform passes clicks with it to the addon and keeps them from the
	 * server.
	 */
	public static final String PREFIX = "identica:dialogs/";
	/**
	 * Click of the button that switches the player to chat.
	 */
	public static final String CHAT = PREFIX + "chat";
	/**
	 * Click of a command button, whose command the proxy runs as the player.
	 */
	public static final String COMMAND = PREFIX + "command";
	/**
	 * Click of a disconnect button, whose player the proxy disconnects.
	 */
	public static final String DISCONNECT = PREFIX + "disconnect";
	/**
	 * Key of the ID of the dialog the button belongs to.
	 */
	public static final String DIALOG = "identica:dialog";
	/**
	 * Key of the button: its index among the dialog's actions, or {@link #EXIT} for the exit action.
	 */
	public static final String BUTTON = "identica:button";
	/**
	 * Value of {@link #BUTTON} for the dialog's exit action.
	 */
	public static final String EXIT = "exit";

	private static final Pattern INPUT = Pattern.compile("\\$\\(([a-zA-Z0-9_]+)\\)");

	private DialogClicks() {
		throw new UnsupportedOperationException("This class cannot be instantiated");
	}

	/**
	 * Returns the values a command or disconnect button carries, next to the inputs the client adds.
	 *
	 * @param dialog the dialog's ID
	 * @param button the button's index among the dialog's actions, or {@link #EXIT}
	 * @return the button's values by key
	 */
	public static @NotNull Map<String, String> button(@NotNull String dialog, @NotNull String button) {
		return Map.of(DIALOG, dialog, BUTTON, button);
	}

	/**
	 * Finds the action a click names in a dialog.
	 *
	 * @param dialog the dialog the player was shown
	 * @param button the click's {@link #BUTTON} value
	 * @return the action, or {@code null} when the dialog has no such button
	 */
	static @Nullable DialogAction action(@NotNull Dialog dialog, @Nullable String button) {
		if (button == null) return null;
		if (EXIT.equals(button)) return dialog.getExitAction();

		try {
			int index = Integer.parseInt(button);
			return index >= 0 && index < dialog.getActions().size() ? dialog.getActions().get(index) : null;
		} catch (NumberFormatException unknown) {
			return null;
		}
	}

	/**
	 * Fills a command template: each {@code $(key)} becomes the value of that input, or nothing when the click has no
	 * such input.
	 *
	 * @param template the command with {@code $(key)} placeholders
	 * @param values the click's values
	 * @return the command line to run
	 */
	static @NotNull String fill(@NotNull String template, @NotNull Map<String, String> values) {
		Matcher matcher = INPUT.matcher(template);
		StringBuilder filled = new StringBuilder();
		while (matcher.find())
			matcher.appendReplacement(filled, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
		matcher.appendTail(filled);

		return filled.toString();
	}
}
