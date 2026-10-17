package me.whereareiam.identica.type.checkpoint;

/**
 * Where a connection waits while one of its steps waits for the player.
 */
public enum CheckpointPlacement {
	/**
	 * On the step's server: routing sends the player there, and the player answers in the game, as in chat. Reaching
	 * the server resumes the journey.
	 */
	STEP_SERVER,

	/**
	 * On the proxy, before it chooses the player's first server: no server sees the player until the journey has
	 * finished, and routing then sends the player straight to where the journey completes. The checkpoint shows the
	 * player the prompts, as the client cannot use chat at that moment.
	 */
	PROXY
}
