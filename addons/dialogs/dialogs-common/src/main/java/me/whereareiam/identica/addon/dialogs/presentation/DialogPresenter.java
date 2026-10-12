package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.type.message.ClientScreen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Keeps the display showing the dialog each player should see. A player keeps a dialog until Identica takes the
 * message it shows down, another dialog replaces it, or they switch to chat or leave. Identica decides when a
 * message's dialog can reach the client and shows it again after a server switch; a dialog a plugin shows directly
 * waits until the player's client is in a server's world.
 */
public final class DialogPresenter implements DialogPlatform.Listener {
	/**
	 * Placeholder of the dialog's own text for the message it shows.
	 */
	private static final String ANSWER = "answer";
	/**
	 * Placeholder of the text of the message the dialog shows, with the messages appended to it on further lines.
	 */
	private static final String MESSAGE = "message";

	private final DialogPlatform platform;
	private final DialogDisplay display;
	private final IdentityService identities;
	private final MessageService messages;
	private final Supplier<Map<String, Dialog>> dialogs;
	private final Map<UUID, Wanted> wanted = new ConcurrentHashMap<>();
	private final Set<UUID> chat = ConcurrentHashMap.newKeySet();

	/**
	 * Creates the presenter.
	 *
	 * @param platform the proxy, which runs commands and disconnects players
	 * @param display gets dialogs onto the players' clients
	 * @param identities finds the player a dialog's text is rendered for
	 * @param messages builds the command line that answers a message's prompt, and tells a player who switches to
	 *                 chat the message there
	 * @param dialogs the current dialogs by ID
	 */
	public DialogPresenter(
			@NotNull DialogPlatform platform,
			@NotNull DialogDisplay display,
			@NotNull IdentityService identities,
			@NotNull MessageService messages,
			@NotNull Supplier<Map<String, Dialog>> dialogs
	) {
		this.platform = platform;
		this.display = display;
		this.identities = identities;
		this.messages = messages;
		this.dialogs = dialogs;
	}

	/**
	 * Shows a player a dialog for an Identica message, replacing the dialog they see.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param dialog ID of the dialog
	 * @param message the message the dialog shows
	 * @return what the player sees now, to {@link #release} once Identica takes the message down
	 */
	public @NotNull Wanted want(@NotNull UUID connectionUniqueId, @NotNull String dialog, @NotNull PlayerMessage message) {
		Map<String, String> placeholders = new HashMap<>(message.getPlaceholders());
		placeholders.put(MESSAGE, message.sequence().stream()
				.map(entry -> fill(entry.getText(), entry.getPlaceholders()))
				.filter(text -> !text.isBlank())
				.collect(Collectors.joining("\n")));

		Wanted shown = new Wanted(dialog, Map.copyOf(placeholders), message);
		wanted.put(connectionUniqueId, shown);
		present(connectionUniqueId);

		return shown;
	}

	/**
	 * Closes the dialog of a message Identica took down, unless the player sees another dialog by now.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param shown what {@link #want} returned
	 */
	public void release(@NotNull UUID connectionUniqueId, @NotNull Wanted shown) {
		if (wanted.remove(connectionUniqueId, shown)) display.clear(connectionUniqueId);
	}

	/**
	 * Shows a player a configured dialog directly, replacing the dialog they see. A player who switched to chat is not
	 * shown it.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param dialog ID of the dialog
	 * @param placeholders values for the dialog's placeholders
	 * @throws IllegalArgumentException when no dialog has that ID
	 */
	public void show(@NotNull UUID connectionUniqueId, @NotNull String dialog, @NotNull Map<String, String> placeholders) {
		if (!dialogs.get().containsKey(dialog)) throw new IllegalArgumentException("No dialog has the ID " + dialog);
		if (chat.contains(connectionUniqueId)) return;

		wanted.put(connectionUniqueId, new Wanted(dialog, Map.copyOf(placeholders), null));
		present(connectionUniqueId);
	}

	/**
	 * Closes the dialog a player sees, if any.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	public void clear(@NotNull UUID connectionUniqueId) {
		if (wanted.remove(connectionUniqueId) != null) display.clear(connectionUniqueId);
	}

	/**
	 * Tells whether a player can be shown a dialog: their client shows dialogs and they did not switch to chat.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @return whether the player can be shown a dialog
	 */
	public boolean shows(@NotNull UUID connectionUniqueId) {
		return !chat.contains(connectionUniqueId) && platform.supports(connectionUniqueId);
	}

	/**
	 * Shows a player whose client entered a server's world the dialog a plugin showed them directly meanwhile, or
	 * again after a server switch. A message's dialog is Identica's to show again.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	public void joined(@NotNull UUID connectionUniqueId) {
		Wanted current = wanted.get(connectionUniqueId);
		if (current != null && current.message() == null) display(connectionUniqueId, current);
	}

	/**
	 * Acts on a button of the dialog the player sees: an answer button answers the prompt of the message the dialog
	 * shows, a command button runs its command as the player, a disconnect button disconnects the player, and the chat
	 * button switches them to chat. Only the buttons of the dialog the player should see now act, so a client cannot
	 * make the proxy run any other command.
	 */
	@Override
	public void clicked(@NotNull UUID connectionUniqueId, @NotNull String click, @NotNull Map<String, String> values) {
		if (DialogClicks.CHAT.equals(click)) {
			switchToChat(connectionUniqueId);
			return;
		}

		Wanted current = wanted.get(connectionUniqueId);
		if (current == null || !current.dialog().equals(values.get(DialogClicks.DIALOG))) return;

		Dialog dialog = dialogs.get().get(current.dialog());
		DialogAction action = dialog == null ? null : DialogClicks.action(dialog, values.get(DialogClicks.BUTTON));
		if (action == null) return;

		if (DialogClicks.DISCONNECT.equals(click) && action.getType() == ActionType.DISCONNECT) {
			platform.disconnect(connectionUniqueId, text(connectionUniqueId, current, dialog).render(action.getValue()));
			return;
		}
		if (!DialogClicks.COMMAND.equals(click)) return;

		String command = command(current, action, values);
		if (command != null) platform.run(connectionUniqueId, command);
	}

	/**
	 * Builds the command a button runs: an answer button's from the prompt of the message the dialog shows, a command
	 * button's from its template.
	 */
	private @Nullable String command(@NotNull Wanted current, @NotNull DialogAction action, @NotNull Map<String, String> values) {
		if (action.getType() == ActionType.COMMAND) return DialogClicks.fill(action.getValue(), values);
		Prompt prompt = current.message() != null ? current.message().getPrompt() : null;
		if (action.getType() != ActionType.ANSWER || prompt == null) return null;

		return messages.answer(prompt, values).orElse(null);
	}

	/**
	 * Switches the player to chat, where Identica tells them the message their dialog showed, so they know what to
	 * answer.
	 */
	private void switchToChat(UUID connectionUniqueId) {
		chat.add(connectionUniqueId);
		Wanted current = wanted.remove(connectionUniqueId);
		display.clear(connectionUniqueId);
		if (current == null || current.message() == null) return;

		identities.findByConnectionUniqueId(connectionUniqueId)
				.ifPresent(identity -> messages.send(identity, current.message()));
	}

	@Override
	public void left(@NotNull UUID connectionUniqueId) {
		chat.remove(connectionUniqueId);
		wanted.remove(connectionUniqueId);
	}

	/**
	 * Shows the dialog a player should see now, if their client is in a server's world.
	 */
	private void present(UUID connectionUniqueId) {
		Wanted current = wanted.get(connectionUniqueId);
		if (current == null || messages.screen(connectionUniqueId) != ClientScreen.WORLD) return;

		display(connectionUniqueId, current);
	}

	private void display(UUID connectionUniqueId, Wanted current) {
		if (!platform.supports(connectionUniqueId)) return;

		Dialog dialog = dialogs.get().get(current.dialog());
		if (dialog == null) return;

		display.show(connectionUniqueId, dialog, text(connectionUniqueId, current, dialog));
	}

	private PlayerText text(UUID connectionUniqueId, Wanted current, Dialog dialog) {
		Map<String, String> placeholders = new HashMap<>(current.placeholders());
		placeholders.put(ANSWER, current.message() == null ? "" : answers(current.message(), dialog));

		Identity identity = identities.findByConnectionUniqueId(connectionUniqueId).orElse(null);
		return new PlayerText(identity, placeholders);
	}

	/**
	 * Returns the dialog's own texts for the message and for each message appended to it, one per line, leaving out
	 * those the dialog has no text for.
	 */
	private static String answers(PlayerMessage message, Dialog dialog) {
		return message.sequence().stream()
				.map(entry -> dialog.getAnswers().getOrDefault(entry.getId(), ""))
				.filter(text -> !text.isBlank())
				.collect(Collectors.joining("\n"));
	}

	private static String fill(String text, Map<String, String> placeholders) {
		String filled = text;
		for (Map.Entry<String, String> placeholder : placeholders.entrySet())
			filled = filled.replace("{" + placeholder.getKey() + "}", placeholder.getValue());

		return filled;
	}

	/**
	 * A dialog a player should see. Each one is its own: a message shown again is a new one, so the release of the
	 * earlier one leaves it alone.
	 */
	public static final class Wanted {
		private final String dialog;
		private final Map<String, String> placeholders;
		private final @Nullable PlayerMessage message;

		private Wanted(@NotNull String dialog, @NotNull Map<String, String> placeholders, @Nullable PlayerMessage message) {
			this.dialog = dialog;
			this.placeholders = placeholders;
			this.message = message;
		}

		@NotNull String dialog() {
			return dialog;
		}

		@NotNull Map<String, String> placeholders() {
			return placeholders;
		}

		/**
		 * The message the dialog shows, or {@code null} for a dialog shown directly.
		 */
		@Nullable PlayerMessage message() {
			return message;
		}
	}
}
