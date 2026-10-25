package me.whereareiam.identica.message;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Holds messages back while a player cannot see them yet, such as while a platform keeps them on the loading screen,
 * and shows them through {@link MessageService} once they can. A platform that can hold players binds its own gate.
 */
@FunctionalInterface
public interface MessageGate {
	/**
	 * Holds a message back for later when the player cannot see it now.
	 *
	 * @param recipient the player
	 * @param message the message
	 * @return {@code true} when the message was held back, {@code false} to show it now
	 */
	boolean hold(@NotNull Identity recipient, @NotNull PlayerMessage message);
}
