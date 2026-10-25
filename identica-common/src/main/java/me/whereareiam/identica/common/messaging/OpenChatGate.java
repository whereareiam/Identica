package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.ChatGate;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Holds no chat back, for platforms that never keep a player from seeing chat.
 */
final class OpenChatGate implements ChatGate {
	@Override
	public boolean hold(@NotNull Identity recipient, @NotNull PlayerMessage message) {
		return false;
	}
}
