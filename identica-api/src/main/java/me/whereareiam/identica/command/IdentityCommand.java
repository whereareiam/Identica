package me.whereareiam.identica.command;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class IdentityCommand {
	protected final @Nullable Identity requireIdentity(@NotNull Actor sender, @Nullable String playerOnlyMessage) {
		if (sender instanceof Identity identity) return identity;
		sendMessageText(sender, playerOnlyMessage);
		return null;
	}

	protected final void sendMessageText(@NotNull Actor sender, @Nullable String message) {
		if (message == null || message.isBlank()) return;
		if (sender instanceof Identity identity) {
			identity.send(message);
			return;
		}

		sender.sendMessage(Serializer.serialize(sender, message));
	}
}
