package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageGate;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Holds no message back, for platforms that never keep a player from seeing messages.
 */
final class OpenMessageGate implements MessageGate {
	@Override
	public boolean hold(@NotNull Identity recipient, @NotNull PlayerMessage message) {
		return false;
	}
}
