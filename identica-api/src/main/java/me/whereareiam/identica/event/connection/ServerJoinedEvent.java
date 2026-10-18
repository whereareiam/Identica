package me.whereareiam.identica.event.connection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.identity.actor.Identity;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player's client is in the world of a server: the proxy handed it the server's join, so the client
 * shows chat, titles and dialogs from now on. Before that, while the client still configures or switches servers, it
 * drops chat and closes dialogs.
 *
 * <p>It fires on the first join and again after every server switch, for a connection with an attached identity.
 * Identica delivers its queued messages, such as the first prompt and the completion message, from this event, and an
 * addon can show its own messages or dialogs from it. Each platform fires it at its own moment: Velocity once the
 * server connection has completed, BungeeCord once it has handed the client the server's join packet.</p>
 */
@Getter
@ToString
@RequiredArgsConstructor
public class ServerJoinedEvent implements Event, SynchronousEvent {
	/**
	 * Identity of the connection whose client joined the server's world.
	 */
	private final @NotNull Identity identity;
	/**
	 * Name of the server the client joined.
	 */
	private final @NotNull String server;
}
