package me.whereareiam.identica.addon.dialogs.presentation;

import java.util.HashMap;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.trigger.StepWaitingTrigger;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerSink;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which dialog each player should see and keeps the display showing it. A player keeps the dialog a
 * moment chose until the moment settles, another moment replaces it, or they switch to chat or leave. A dialog is
 * shown while the player's client is in a server's world, which Identica reports with its {@code ServerJoinedEvent},
 * or while Identica holds the player on the loading screen for the addon's checkpoint: one that cannot reach the
 * client yet, such as during a switch to another server, is shown once the client is in the new world. A client that
 * cannot show dialogs never gets one and answers Identica's chat prompts.
 */
public final class DialogPresenter implements TriggerSink, DialogPlatform.Listener {
	/**
	 * Attribute and placeholder of why Identica asks again.
	 */
	private static final String ANSWER = "answer";

	private final DialogPlatform platform;
	private final DialogDisplay display;
	private final IdentityService identities;
	private final Supplier<List<DialogRule>> rules;
	private final Supplier<Map<String, Dialog>> dialogs;
	private final Map<UUID, Wanted> wanted = new ConcurrentHashMap<>();
	private final Set<UUID> chat = ConcurrentHashMap.newKeySet();
	private final Set<UUID> inWorld = ConcurrentHashMap.newKeySet();
	private final Map<UUID, CheckpointStop> held = new ConcurrentHashMap<>();

	/**
	 * Creates the presenter.
	 *
	 * @param platform the proxy
	 * @param display how dialogs reach players' clients on the proxy
	 * @param identities Identica's identities, for rendering text as Identica renders it for the player
	 * @param rules the current rules
	 * @param dialogs the current dialogs by ID
	 */
	public DialogPresenter(
			@NotNull DialogPlatform platform,
			@NotNull DialogDisplay display,
			@NotNull IdentityService identities,
			@NotNull Supplier<List<DialogRule>> rules,
			@NotNull Supplier<Map<String, Dialog>> dialogs
	) {
		this.platform = platform;
		this.display = display;
		this.identities = identities;
		this.rules = rules;
		this.dialogs = dialogs;
	}

	@Override
	public void fire(@NotNull TriggerContext context) {
		UUID connectionUniqueId = context.getConnectionUniqueId();
		if (chat.contains(connectionUniqueId)) return;

		String dialog = RuleMatcher.first(rules.get(), context).map(DialogRule::getDialog).orElse(null);
		if (dialog == null) {
			withoutDialog(connectionUniqueId, context.getTrigger());
			return;
		}
		if (!dialogs.get().containsKey(dialog)) {
			Logger.warn("A dialog rule names the dialog %s, which no file in dialogs/ defines", dialog);
			withoutDialog(connectionUniqueId, context.getTrigger());
			return;
		}

		wanted.put(connectionUniqueId, new Wanted(dialog, context.getPlaceholders(), context.getTrigger(), context.getAttributes().get(ANSWER)));
		present(connectionUniqueId);
	}

	/**
	 * Settles a moment that shows no dialog. A player on the loading screen could not answer a waiting step there, so
	 * they go on to the step's server, where they answer in chat.
	 */
	private void withoutDialog(UUID connectionUniqueId, String trigger) {
		settle(connectionUniqueId, trigger);
		if (!StepWaitingTrigger.ID.equals(trigger)) return;

		CheckpointStop stop = held.get(connectionUniqueId);
		if (stop == null || stop.getPlacement() != CheckpointPlacement.LOADING_SCREEN) return;

		held.remove(connectionUniqueId, stop);
		stop.handOver();
	}

	@Override
	public void settle(@NotNull UUID connectionUniqueId, @NotNull String trigger) {
		Wanted current = wanted.get(connectionUniqueId);
		if (current == null || !current.trigger().equals(trigger)) return;

		wanted.remove(connectionUniqueId, current);
		display.clear(connectionUniqueId);
	}

	/**
	 * Shows a dialog to a player directly, outside any trigger.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param dialog the dialog's ID
	 * @param placeholders values for the dialog's placeholders
	 * @throws IllegalArgumentException when no dialog has that ID
	 */
	public void show(@NotNull UUID connectionUniqueId, @NotNull String dialog, @NotNull Map<String, String> placeholders) {
		if (!dialogs.get().containsKey(dialog)) throw new IllegalArgumentException("No dialog has the ID " + dialog);
		if (chat.contains(connectionUniqueId)) return;

		wanted.put(connectionUniqueId, new Wanted(dialog, Map.copyOf(placeholders), "", null));
		present(connectionUniqueId);
	}

	/**
	 * Closes the dialog the player should see, if any.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	public void clear(@NotNull UUID connectionUniqueId) {
		if (wanted.remove(connectionUniqueId) != null) display.clear(connectionUniqueId);
	}

	/**
	 * Records that the player's client is in a server's world, where a dialog reaches it, and shows the dialog the
	 * player should see. A server switch closed every dialog on the client, so this shows it again.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	public void joined(@NotNull UUID connectionUniqueId) {
		inWorld.add(connectionUniqueId);
		present(connectionUniqueId);
	}

	/**
	 * Takes a player Identica holds at the addon's checkpoint: on the loading screen, where the client shows a dialog
	 * but no chat, the player sees the dialog they should see right away. Their answers run through the checkpoint
	 * until the hold ends.
	 *
	 * @param stop the held player
	 */
	public void held(@NotNull CheckpointStop stop) {
		UUID connectionUniqueId = stop.getConnectionUniqueId();
		held.put(connectionUniqueId, stop);
		stop.released().whenComplete((ignored, failure) -> held.remove(connectionUniqueId, stop));
		present(connectionUniqueId);
	}

	@Override
	public void switchingServer(@NotNull UUID connectionUniqueId) {
		inWorld.remove(connectionUniqueId);
	}

	/**
	 * Handles a click of the addon's buttons: the chat button switches the player to chat, a command button runs its
	 * command as the player, through the checkpoint for a held player and on the proxy otherwise, and a disconnect
	 * button disconnects the player, also one held on the loading screen. Only the buttons of the dialog the player
	 * should see now act, so a client cannot make the proxy run any other command.
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
		if (!DialogClicks.COMMAND.equals(click) || action.getType() != ActionType.COMMAND) return;

		String command = DialogClicks.fill(action.getValue(), values);
		CheckpointStop stop = held.get(connectionUniqueId);
		if (stop == null) {
			platform.run(connectionUniqueId, command);
			return;
		}

		stop.answer(command);
	}

	/**
	 * Switches the player to chat; a player held at the checkpoint goes on to the step's server for it, since the
	 * loading screen shows no chat.
	 */
	private void switchToChat(UUID connectionUniqueId) {
		CheckpointStop stop = held.remove(connectionUniqueId);
		if (stop != null) stop.handOver();

		chat.add(connectionUniqueId);
		wanted.remove(connectionUniqueId);
		display.clear(connectionUniqueId);
	}

	@Override
	public void left(@NotNull UUID connectionUniqueId) {
		chat.remove(connectionUniqueId);
		inWorld.remove(connectionUniqueId);
		held.remove(connectionUniqueId);
		wanted.remove(connectionUniqueId);
	}

	private void present(UUID connectionUniqueId) {
		Wanted current = wanted.get(connectionUniqueId);
		if (current == null || !reachable(connectionUniqueId) || !platform.supports(connectionUniqueId)) return;

		Dialog dialog = dialogs.get().get(current.dialog());
		if (dialog == null) return;

		display.show(connectionUniqueId, dialog, text(connectionUniqueId, current, dialog));
	}

	/**
	 * Tells whether a dialog reaches the player's client now: in a server's world, or on the loading screen while
	 * Identica holds the player there.
	 */
	private boolean reachable(UUID connectionUniqueId) {
		if (inWorld.contains(connectionUniqueId)) return true;

		CheckpointStop stop = held.get(connectionUniqueId);
		return stop != null && stop.getPlacement() == CheckpointPlacement.LOADING_SCREEN;
	}

	/**
	 * Renders the dialog's text for the player: the moment's placeholders, and {@code {answer}} as the dialog's text for
	 * why Identica asks again.
	 */
	private PlayerText text(UUID connectionUniqueId, Wanted current, Dialog dialog) {
		Map<String, String> placeholders = new HashMap<>(current.placeholders());
		placeholders.put(ANSWER, current.answer() == null ? "" : dialog.getAnswers().getOrDefault(current.answer(), ""));

		Identity identity = identities.findByConnectionUniqueId(connectionUniqueId).orElse(null);
		return new PlayerText(identity, placeholders);
	}

	/**
	 * The dialog a player should see, with the trigger that chose it, or an empty trigger for one shown directly, and
	 * the ID of why Identica asks again, if it does.
	 */
	private record Wanted(
			@NotNull String dialog,
			@NotNull Map<String, String> placeholders,
			@NotNull String trigger,
			@Nullable String answer
	) {
	}
}
