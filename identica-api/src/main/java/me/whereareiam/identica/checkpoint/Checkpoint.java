package me.whereareiam.identica.checkpoint;

import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

/**
 * Where a connection waits while one of its steps waits for the player, and how the player is asked there.
 *
 * <p>Identica asks {@link Checkpoints} for a checkpoint every time a step waits. Without any registered checkpoint,
 * or when none accepts, the connection waits on the step's server, where providers ask in chat and the player
 * answers with their commands. A checkpoint that holds the connection elsewhere receives it as a
 * {@link CheckpointStop}, resumes the journey there and answers through the provider's commands; providers stay
 * unchanged.</p>
 *
 * <pre>{@code
 * public final class LoadingScreenCheckpoint implements Checkpoint {
 *     public String id() { return "loading-screen"; }
 *     public CheckpointPlacement placement() { return CheckpointPlacement.PROXY; }
 *     public boolean accepts(CheckpointRequest request) { return showsDialogs(request.getConnectionUniqueId()); }
 *     public void arrived(CheckpointStop stop) {
 *         stops.put(stop.getConnectionUniqueId(), stop);
 *         stop.resume();
 *     }
 * }
 * }</pre>
 */
public interface Checkpoint {
	/**
	 * Returns the checkpoint's id, which logs name it by.
	 *
	 * @return the id
	 */
	@NotNull String id();

	/**
	 * Tells where the connection waits at this checkpoint. A checkpoint whose placement the platform cannot hold is
	 * never picked.
	 *
	 * @return the placement
	 */
	@NotNull CheckpointPlacement placement();

	/**
	 * Tells whether this checkpoint takes a connection whose step waits. It is asked for each waiting step of the
	 * journey, so it should answer the same way for one connection.
	 *
	 * @param request the waiting step
	 * @return whether this checkpoint takes the connection
	 */
	boolean accepts(@NotNull CheckpointRequest request);

	/**
	 * Hands over a connection the platform now holds at this checkpoint. The checkpoint resumes the journey through
	 * {@link CheckpointStop#resume()}; the hold ends once the journey has finished or the player left.
	 *
	 * @param stop the held connection
	 */
	void arrived(@NotNull CheckpointStop stop);
}
