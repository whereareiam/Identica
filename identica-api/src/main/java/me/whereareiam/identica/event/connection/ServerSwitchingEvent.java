package me.whereareiam.identica.event.connection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.identity.actor.Identity;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player's client leaves the world it is in to join another server: from now until
 * {@link ServerJoinedEvent}, it configures for the new server and shows no chat, titles or dialogs, and it closes the
 * dialogs it had open.
 *
 * <p>It fires for a connection with an attached identity, at each platform's own moment: Velocity once the new server
 * accepted the player, BungeeCord once the client started configuring for the new server.</p>
 */
@Getter
@ToString
@RequiredArgsConstructor
public class ServerSwitchingEvent implements Event, SynchronousEvent {
	/**
	 * Identity of the connection whose client leaves its server's world.
	 */
	private final @NotNull Identity identity;
}
