package me.whereareiam.identica.type.checkpoint;

/**
 * Where a connection waits at a {@link me.whereareiam.identica.checkpoint.Checkpoint} while one of its steps waits
 * for the player.
 */
public enum CheckpointPlacement {
	/**
	 * On the loading screen, before the player enters any server's world. The client is in its configuration phase,
	 * where it shows no chat, so the checkpoint shows the player the prompts itself, for example as dialogs. Once the
	 * journey has finished, the player enters the server the journey completes on.
	 *
	 * <p>Velocity holds the player on the proxy, with no server connected, for clients from Minecraft 1.20.5 on, which
	 * answer the cookie requests that keep them connected. BungeeCord holds the player in the configuration of the
	 * first server it connects them to, for clients from Minecraft 1.20.2 on; that server sees a connecting player,
	 * but the player does not enter its world until the hold ends. A journey that completes on another server then
	 * switches the player there from the first server's world.</p>
	 */
	LOADING_SCREEN,

	/**
	 * On the step's server: routing sends the player there as without a checkpoint, and the checkpoint receives the
	 * connection once the player is in that server's world. Every platform holds it.
	 */
	STEP_SERVER
}
