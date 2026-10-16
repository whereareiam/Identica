package me.whereareiam.identica.checkpoint;

import me.whereareiam.identica.model.auth.ConnectionDecision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A connection the platform holds at a {@link Checkpoint}, as Identica sees it. Platforms implement it and hand it to
 * {@link Checkpoints#arrive(HeldConnection)}; the checkpoint receives a {@link CheckpointStop} backed by it.
 */
public interface HeldConnection {
	/**
	 * Returns the held connection's unique id on the proxy.
	 *
	 * @return the connection's unique id
	 */
	@NotNull UUID getConnectionUniqueId();

	/**
	 * Continues the connection's journey where it is held.
	 *
	 * @return the journey's decision
	 */
	@NotNull CompletionStage<ConnectionDecision> resume();

	/**
	 * Runs a command as the held player.
	 *
	 * @param command the command line, without a leading slash
	 * @return whether the platform ran a command
	 */
	@NotNull CompletionStage<Boolean> answer(@NotNull String command);

	/**
	 * Ends the hold. Identica calls it once the connection no longer waits here; the platform then lets the player
	 * go on to the server routing sends it to.
	 *
	 * @param destination the server routing sends the player to next, or {@code null} when it routes the player
	 *                    nowhere, such as for a cancelled journey
	 */
	void release(@Nullable String destination);

	/**
	 * Ends the hold of a connection handed over to the step's server. The journey still waits there, so the platform
	 * continues it once the player reaches that server, exactly as for a player who was never held.
	 *
	 * @param destination the step's server routing sends the player to, or {@code null} when routing sends the player
	 *                    nowhere
	 */
	void handOver(@Nullable String destination);

	/**
	 * Completes once the hold has ended, also when the player left.
	 *
	 * @return the end of the hold
	 */
	@NotNull CompletionStage<Void> released();
}
