package me.whereareiam.identica.command;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base of a command players run, which answers them with Identica's messages.
 */
public abstract class IdentityCommand {
	protected final @Nullable Identity requireIdentity(@NotNull Actor sender, @Nullable PlayerMessage playerOnlyMessage) {
		if (sender instanceof Identity identity) return identity;
		sendMessage(sender, playerOnlyMessage);
		return null;
	}

	/**
	 * Answers the sender: a player through {@link MessageService}, in every way the message is presented, anyone else,
	 * such as the console, with its chat text.
	 *
	 * @param sender who ran the command
	 * @param message the answer, or {@code null} for none
	 */
	protected final void sendMessage(@NotNull Actor sender, @Nullable PlayerMessage message) {
		if (message == null || message.isEmpty()) return;
		if (sender instanceof Identity identity) {
			messageService().send(identity, message);
			return;
		}

		if (!message.getText().isBlank())
			sender.sendMessage(Serializer.serialize(sender, message.getText(), message.getPlaceholders()));
	}

	/**
	 * Returns the service that shows players the command's answers.
	 *
	 * @return message service
	 */
	protected abstract @NotNull MessageService messageService();
}
