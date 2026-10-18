package me.whereareiam.identica.checkpoint;

import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Where a connection waits while one of its steps waits for the player, and how the player is asked there.
 *
 * <p>Identica asks {@link Checkpoints} for a checkpoint every time a step waits. Without any registered checkpoint,
 * or when none accepts, the connection waits on the step's server, where providers ask in chat and the player
 * answers with their commands. A checkpoint receives the connection it holds as a {@link CheckpointStop}, shows the
 * player what the waiting step asks for and answers through the provider's commands; providers stay unchanged.</p>
 *
 * <p>A checkpoint lists the placements it can work with, most wanted first. Identica holds the connection at the
 * first one the platform can hold for it, so one checkpoint can ask on the loading screen where the platform allows
 * it and on the step's server elsewhere. Identica keeps a held client connected, continues the journey once the
 * checkpoint has received the connection, and delays its own replies to a player on the loading screen, such as the
 * answer to a wrong password, until the player has entered their first server.</p>
 *
 * <pre>{@code
 * public final class LoadingScreenCheckpoint implements Checkpoint {
 *     public String id() { return "loading-screen"; }
 *     public List<CheckpointPlacement> placements() {
 *         return List.of(CheckpointPlacement.LOADING_SCREEN, CheckpointPlacement.STEP_SERVER);
 *     }
 *     public boolean accepts(CheckpointRequest request) { return showsDialogs(request.getConnectionUniqueId()); }
 *     public void arrived(CheckpointStop stop) {
 *         stops.put(stop.getConnectionUniqueId(), stop);
 *         stop.getWaiting().ifPresent(waiting -> ask(stop, waiting));
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
	 * Lists where the checkpoint can hold a connection, most wanted first. Identica holds a connection at the first
	 * placement the platform can hold for it; a checkpoint none of whose placements the platform can hold is never
	 * picked.
	 *
	 * @return the placements, most wanted first
	 */
	@NotNull List<CheckpointPlacement> placements();

	/**
	 * Tells whether this checkpoint takes a connection whose step waits. It is asked for each waiting step of the
	 * journey, so it should answer the same way for one connection.
	 *
	 * @param request the waiting step
	 * @return whether this checkpoint takes the connection
	 */
	boolean accepts(@NotNull CheckpointRequest request);

	/**
	 * Hands over a connection Identica now holds at this checkpoint. On the loading screen, Identica continues the
	 * journey right after this returns, and the next step that waits for input is reported through
	 * {@link me.whereareiam.identica.event.step.StepFinishedEvent}. On the step's server, the journey already
	 * waits for input, and {@link CheckpointStop#getWaiting()} tells for what. The hold ends once the journey has
	 * finished, the connection was handed over, or the player left.
	 *
	 * @param stop the held connection
	 */
	void arrived(@NotNull CheckpointStop stop);
}
