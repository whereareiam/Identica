package me.whereareiam.identica.message;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tells whether a platform holds a player on the loading screen, where the client shows no chat, titles or bars but
 * can show dialogs. Part renderers ask through {@link MessageService#screen(UUID)}. A platform that can hold players
 * binds its own; otherwise no player is ever held.
 */
@FunctionalInterface
public interface LoadingScreen {
	/**
	 * Tells whether the platform holds a connection on the loading screen now.
	 *
	 * @param connectionUniqueId the connection
	 * @return {@code true} while the connection is held
	 */
	boolean holds(@NotNull UUID connectionUniqueId);
}
