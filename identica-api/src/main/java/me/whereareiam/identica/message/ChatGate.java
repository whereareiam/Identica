package me.whereareiam.identica.message;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Holds back chat a player cannot see yet, such as while a platform keeps them on the loading screen, and shows it
 * once they can. Only the chat part waits: a message's other parts, such as a dialog the client shows on the loading
 * screen, are shown as usual. A platform that can hold players binds its own gate.
 */
@FunctionalInterface
public interface ChatGate {
	/**
	 * Holds a message's chat back for later when the player cannot see chat now.
	 *
	 * @param recipient the player
	 * @param message the message as chat, with the text its chat part shows
	 * @return {@code true} when the chat was held back, {@code false} to show it now
	 */
	boolean hold(@NotNull Identity recipient, @NotNull PlayerMessage message);
}
