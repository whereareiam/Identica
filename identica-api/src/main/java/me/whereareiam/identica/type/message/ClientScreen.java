package me.whereareiam.identica.type.message;

/**
 * What a player's client shows now, which decides what parts of a message it can show.
 */
public enum ClientScreen {
	/**
	 * The loading screen, where a platform holds the player before they enter any server: the client shows dialogs but
	 * no chat, titles or bars.
	 */
	LOADING_SCREEN,
	/**
	 * Joining a server: before the client's first server, or while it switches to another one. It shows nothing of a
	 * message until it joined that server's world.
	 */
	JOINING,
	/**
	 * A server's world, where the client shows every part of a message.
	 */
	WORLD
}
