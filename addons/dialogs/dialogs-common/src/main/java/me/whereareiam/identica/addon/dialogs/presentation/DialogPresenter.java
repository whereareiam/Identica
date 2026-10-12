package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerSink;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Decides which dialog each player should see and keeps the platform showing it. A player keeps the dialog a
 * moment chose until the moment settles, another moment replaces it, or they switch to chat or leave. A dialog
 * that cannot reach the client yet, such as before the player joined a server, is shown once they connect; a
 * client that cannot show dialogs never gets one and answers Identica's chat prompts.
 */
public final class DialogPresenter implements TriggerSink, PlatformListener {
	private final DialogPlatform platform;
	private final IdentityService identities;
	private final Supplier<List<DialogRule>> rules;
	private final Supplier<Map<String, Dialog>> dialogs;
	private final Map<UUID, Wanted> wanted = new ConcurrentHashMap<>();
	private final Set<UUID> chat = ConcurrentHashMap.newKeySet();

	/**
	 * Creates the presenter.
	 *
	 * @param platform the proxy's dialogs
	 * @param identities Identica's identities, for rendering text as Identica renders it for the player
	 * @param rules the current rules
	 * @param dialogs the current dialogs by ID
	 */
	public DialogPresenter(
			@NotNull DialogPlatform platform,
			@NotNull IdentityService identities,
			@NotNull Supplier<List<DialogRule>> rules,
			@NotNull Supplier<Map<String, Dialog>> dialogs
	) {
		this.platform = platform;
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
			settle(connectionUniqueId, context.getTrigger());
			return;
		}
		if (!dialogs.get().containsKey(dialog)) {
			Logger.warn("A dialog rule names the dialog %s, which no file in dialogs/ defines", dialog);
			settle(connectionUniqueId, context.getTrigger());
			return;
		}

		wanted.put(connectionUniqueId, new Wanted(dialog, context.getPlaceholders(), context.getTrigger()));
		present(connectionUniqueId);
	}

	@Override
	public void settle(@NotNull UUID connectionUniqueId, @NotNull String trigger) {
		Wanted current = wanted.get(connectionUniqueId);
		if (current == null || !current.trigger().equals(trigger)) return;

		wanted.remove(connectionUniqueId, current);
		platform.clear(connectionUniqueId);
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

		wanted.put(connectionUniqueId, new Wanted(dialog, Map.copyOf(placeholders), ""));
		present(connectionUniqueId);
	}

	/**
	 * Closes the dialog the player should see, if any.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	public void clear(@NotNull UUID connectionUniqueId) {
		if (wanted.remove(connectionUniqueId) != null) platform.clear(connectionUniqueId);
	}

	@Override
	public void connected(@NotNull UUID connectionUniqueId) {
		present(connectionUniqueId);
	}

	@Override
	public void switchedToChat(@NotNull UUID connectionUniqueId) {
		chat.add(connectionUniqueId);
		wanted.remove(connectionUniqueId);
		platform.clear(connectionUniqueId);
	}

	@Override
	public void left(@NotNull UUID connectionUniqueId) {
		chat.remove(connectionUniqueId);
		wanted.remove(connectionUniqueId);
	}

	private void present(UUID connectionUniqueId) {
		Wanted current = wanted.get(connectionUniqueId);
		if (current == null || !platform.supports(connectionUniqueId)) return;

		Dialog dialog = dialogs.get().get(current.dialog());
		if (dialog == null) return;

		Identity identity = identities.findByConnectionUniqueId(connectionUniqueId).orElse(null);
		platform.show(connectionUniqueId, dialog, new PlayerText(identity, current.placeholders()));
	}

	/**
	 * The dialog a player should see, with the trigger that chose it, or an empty trigger for one shown directly.
	 */
	private record Wanted(@NotNull String dialog, @NotNull Map<String, String> placeholders, @NotNull String trigger) {
	}
}
