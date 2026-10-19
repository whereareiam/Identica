package me.whereareiam.identica.type.pipeline.journey.step;

/**
 * What a journey step needs before it can run.
 *
 * <p>The requirement decides when the step runs and where the player waits for it. A step that only needs the login
 * runs while the player logs in, so a journey it finishes is done before the proxy picks the player's first server.
 * A step that needs the player online stops the journey until the player has joined a server, and the player is sent
 * to the server of that step first. A step declares {@link #ONLINE} only when it talks to the player.</p>
 */
public enum StepContextRequirement {
	/**
	 * The step decides from the login alone: it reads the profile, the provider context and stored data, and does not
	 * talk to the player. It may still end waiting for the player's input, which sends the player to the step's
	 * server like any waiting step.
	 */
	LOGIN,

	/**
	 * The step talks to the player, so the player must be online as an attached identity. The journey waits before
	 * this step, and the player is sent to the step's server to meet it.
	 */
	ONLINE
}
