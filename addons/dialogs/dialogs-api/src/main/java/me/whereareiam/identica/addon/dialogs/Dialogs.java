package me.whereareiam.identica.addon.dialogs;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The dialogs addon: the dialogs and rules an administrator configured, and showing dialogs to players directly.
 *
 * <p>The addon shows Identica's messages as dialogs through Identica's {@code MessageService}: it registers the
 * {@link DialogPart} type and presents every prompt and message its rules name, so the first rule whose conditions
 * match picks the dialog when Identica tells a player the message. A player whose client cannot show dialogs, or who
 * switched to chat, gets the message in chat. Another plugin shows a message as a dialog of its own choice by
 * presenting it with {@link DialogPart#of(String)}.</p>
 *
 * <pre>{@code
 * Dialogs.whenReady(dialogs -> dialogs.show(connectionUniqueId, "welcome", Map.of("player", name)));
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
}
