package me.whereareiam.identica.checkpoint;

import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * The registered {@link Checkpoint}s. For a waiting step, the most recently registered checkpoint that accepts it and
 * has a placement the platform can hold for the connection is picked, at the first such placement; without one, the
 * connection waits on the step's server as without checkpoints.
 *
 * <pre>{@code
 * Checkpoints checkpoints = identica.getCheckpoints();
 * checkpoints.register(loadingScreen);
 * // when the addon stops
 * checkpoints.unregister(loadingScreen);
 * }</pre>
 */
public interface Checkpoints {
	/**
	 * Registers a checkpoint, which is asked before the ones registered earlier.
	 *
	 * @param checkpoint the checkpoint
	 */
	void register(@NotNull Checkpoint checkpoint);

	/**
	 * Unregisters a checkpoint. Connections it already holds stay held until their journey finishes.
	 *
	 * @param checkpoint the checkpoint
	 */
	void unregister(@NotNull Checkpoint checkpoint);

	/**
	 * Picks the checkpoint for a waiting step and remembers it for the connection until its journey finishes.
	 * Identica calls it whenever a step waits. A connection keeps the checkpoint that holds it, and a connection
	 * handed over to the step's server keeps waiting there, until its journey finishes.
	 *
	 * @param request the waiting step
	 * @return the picked checkpoint and where it holds the connection, or empty when the connection waits on the
	 * step's server without a checkpoint
	 */
	@NotNull Optional<CheckpointSelection> select(@NotNull CheckpointRequest request);

	/**
	 * Returns the checkpoint picked for a connection whose journey waits.
	 *
	 * @param connectionUniqueId the connection's unique id on the proxy
	 * @return the checkpoint and where it holds the connection, or empty when nothing of the connection waits or it
	 * waits on the step's server without a checkpoint
	 */
	@NotNull Optional<CheckpointSelection> selected(@NotNull UUID connectionUniqueId);

	/**
	 * Tells whether the platform holds a connection at a checkpoint right now.
	 *
	 * @param connectionUniqueId the connection's unique id on the proxy
	 * @return whether the connection is held
	 */
	boolean holds(@NotNull UUID connectionUniqueId);

	/**
	 * Hands a connection the platform now holds on the loading screen to its picked checkpoint, continues its journey
	 * and releases it when the journey no longer waits. Platforms call it; a connection without a checkpoint picked
	 * for the loading screen is released at once.
	 *
	 * @param connection the held connection
	 */
	void arrive(@NotNull HeldConnection connection);
}
