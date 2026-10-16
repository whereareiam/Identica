package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.identity.actor.Identity;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Renders a dialog's text for one player as Identica renders its messages. The dialog's own placeholders are put in
 * first, so a value that holds Identica's placeholders, such as an answer that starts with {@code {prefix}}, has
 * them rendered too.
 */
final class PlayerText implements DialogText {
	private final @Nullable Identity identity;
	private final Map<String, String> placeholders;
	private final CommandService commands;

	PlayerText(@Nullable Identity identity, @NotNull Map<String, String> placeholders, @NotNull CommandService commands) {
		this.identity = identity;
		this.placeholders = Map.copyOf(placeholders);
		this.commands = commands;
	}

	/**
	 * Returns the dialog's own placeholders.
	 */
	@NotNull Map<String, String> placeholders() {
		return placeholders;
	}

	@Override
	public @NotNull Component render(@NotNull String text) {
		String filled = text;
		for (Map.Entry<String, String> placeholder : placeholders.entrySet())
			filled = filled.replace("{" + placeholder.getKey() + "}", placeholder.getValue());

		if (identity == null) return Serializer.serialize(filled);
		return Serializer.serialize(identity, filled);
	}

	/**
	 * Resolves an Identica command by its definition, with the name an administrator gave it.
	 */
	@Override
	public @Nullable String command(@NotNull DialogAction action) {
		if (action.getType() != ActionType.COMMAND) return DialogText.super.command(action);

		return commands.getCommandLine(action.getValue())
				.map(line -> action.getArguments().isEmpty() ? line : line + " " + String.join(" ", action.getArguments()))
				.orElse(null);
	}
}
