package me.whereareiam.identica.event.connection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a connection was refused because another connection's login in progress holds its username.
 * The holder came from another address and keeps logging in; the refused connection may try again once that login
 * has finished, the holder has left or the login has timed out.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class UsernameHeldEvent implements Event, SynchronousEvent {
	/**
	 * Unique id of the refused connection.
	 */
	private final @Nullable UUID connectionUniqueId;
	/**
	 * Username the refused connection logged in under.
	 */
	private final @NotNull String username;
	/**
	 * Address the refused connection came from, or {@code null} when unknown.
	 */
	private final @Nullable String ip;
}
