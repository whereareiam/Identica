package me.whereareiam.identica.addon.dialogs;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.trigger.DialogTrigger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The dialogs addon: the dialogs and rules an administrator configured, the triggers that decide when a rule may
 * show a dialog, and showing dialogs to players directly.
 *
 * <p>A trigger reports a moment, such as an Identica step waiting for the player's input. The first rule whose
 * trigger and conditions match picks the dialog to show; a player whose client cannot show dialogs keeps the chat
 * prompts Identica sends anyway.</p>
 *
 * <pre>{@code
 * Dialogs.whenReady(dialogs -> dialogs.register(new MyTrigger()));
 * }</pre>
 */
public interface Dialogs {
	/**
	 * Returns the running addon.
	 *
	 * @return the running addon
	 * @throws IllegalStateException when the addon is not running
	 */
	static @NotNull Dialogs get() {
		return RunningDialogs.get();
	}

	/**
	 * Returns the running addon, or empty when it is not running.
	 *
	 * @return the running addon, if any
	 */
	static @NotNull Optional<Dialogs> find() {
		return RunningDialogs.find();
	}

	/**
	 * Runs the action once the addon is running, with its dialogs and rules loaded. When it already is, the action
	 * runs immediately; otherwise it runs once, on the thread that starts the addon.
	 *
	 * @param action action to run with the addon
	 */
	static void whenReady(@NotNull Consumer<Dialogs> action) {
		RunningDialogs.whenReady(action);
	}

	/**
	 * Registers the running addon and runs the actions waiting in {@link #whenReady(Consumer)}. Called by the addon
	 * when it starts; other plugins never call it.
	 *
	 * @param dialogs the running addon
	 * @throws IllegalStateException when an addon is already registered
	 */
	@ApiStatus.Internal
	static void register(@NotNull Dialogs dialogs) {
		RunningDialogs.register(dialogs);
	}

	/**
	 * Removes the running addon. Called by the addon when it stops; other plugins never call it.
	 */
	@ApiStatus.Internal
	static void unregister() {
		RunningDialogs.unregister();
	}

	/**
	 * Finds a configured dialog.
	 *
	 * @param id the dialog's ID, the name of its file without the extension
	 * @return the dialog, or empty when none has that ID
	 */
	@NotNull Optional<Dialog> dialog(@NotNull String id);

	/**
	 * Returns the configured rules in the order they are tried.
	 *
	 * @return the rules
	 */
	@NotNull List<DialogRule> rules();

	/**
	 * Shows a configured dialog to a player. A player whose client cannot show dialogs, or who switched to chat,
	 * is not shown it.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param dialogId the dialog's ID
	 * @param placeholders values for the dialog's {@code {placeholders}}
	 * @throws IllegalArgumentException when no dialog has that ID
	 */
	void show(@NotNull UUID connectionUniqueId, @NotNull String dialogId, @NotNull Map<String, String> placeholders);

	/**
	 * Closes the dialog the addon showed a player, if any.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	void clear(@NotNull UUID connectionUniqueId);

	/**
	 * Adds a trigger, which rules can then name. The addon starts it immediately.
	 *
	 * @param trigger the trigger
	 * @throws IllegalArgumentException when a trigger with the same ID is registered
	 */
	void register(@NotNull DialogTrigger trigger);

	/**
	 * Stops and removes a trigger.
	 *
	 * @param trigger the trigger
	 */
	void unregister(@NotNull DialogTrigger trigger);
}
