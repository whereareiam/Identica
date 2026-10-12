package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.pipeline.PendingScenarioService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Picks the dialog that shows an Identica message to a player: the one the first rule whose conditions match names,
 * if a file in {@code dialogs/} defines it.
 */
public final class DialogChooser {
	private final PendingScenarioService scenarios;
	private final Supplier<List<DialogRule>> rules;
	private final Supplier<Map<String, Dialog>> dialogs;

	/**
	 * Creates the chooser.
	 *
	 * @param scenarios tells a player's pending scenario, for the {@code scenario} condition
	 * @param rules the current rules
	 * @param dialogs the current dialogs by ID
	 */
	public DialogChooser(
			@NotNull PendingScenarioService scenarios,
			@NotNull Supplier<List<DialogRule>> rules,
			@NotNull Supplier<Map<String, Dialog>> dialogs
	) {
		this.scenarios = scenarios;
		this.rules = rules;
		this.dialogs = dialogs;
	}

	/**
	 * Picks the dialog for a message.
	 *
	 * @param message the message
	 * @param identity the player's connection, for the {@code host} condition
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @return the dialog's ID, or {@code null} when no rule shows one or the dialog it names does not exist
	 */
	public @Nullable String choose(@NotNull PlayerMessage message, @NotNull ConnectionIdentity identity, @NotNull UUID connectionUniqueId) {
		String dialog = RuleMatcher.first(rules.get(), attributes(message, identity, connectionUniqueId))
				.map(DialogRule::getDialog)
				.orElse(null);

		return dialog == null ? null : existing(message, dialog);
	}

	/**
	 * Checks that a dialog a message is to be shown as exists.
	 *
	 * @param message the message
	 * @param dialog the dialog's ID
	 * @return the dialog's ID, or {@code null} when no file in {@code dialogs/} defines it
	 */
	public @Nullable String existing(@NotNull PlayerMessage message, @NotNull String dialog) {
		if (dialogs.get().containsKey(dialog)) return dialog;

		Logger.warn("The message %s is to be shown as the dialog %s, which no file in dialogs/ defines", message.getId(), dialog);
		return null;
	}

	private Map<String, String> attributes(PlayerMessage message, ConnectionIdentity identity, UUID connectionUniqueId) {
		Map<String, String> attributes = new HashMap<>();
		attributes.put(RuleMatcher.MESSAGE, message.getId());

		Prompt prompt = message.getPrompt();
		if (prompt != null) attributes.put(RuleMatcher.PROMPT, prompt.getId());

		scenarios.find(connectionUniqueId).ifPresent(scenario -> attributes.put(RuleMatcher.SCENARIO, scenario.name()));

		ConnectionIdentity.Origin origin = identity.getOrigin();
		if (origin != null) attributes.put(RuleMatcher.HOST, origin.getHost());

		return attributes;
	}
}
