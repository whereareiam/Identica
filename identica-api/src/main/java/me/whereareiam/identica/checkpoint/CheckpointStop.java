package me.whereareiam.identica.checkpoint;

import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A connection Identica holds at a {@link Checkpoint}, as the checkpoint sees it.
 */
public interface CheckpointStop {
	/**
	 * Returns the held connection's unique id on the proxy.
	 *
	 * @return the connection's unique id
	 */
	@NotNull UUID getConnectionUniqueId();

	/**
	 * Tells where the connection is held: on the loading screen, where the client shows no chat, or on the step's
	 * server, in the server's world.
	 *
	 * @return the placement
	 */
	@NotNull CheckpointPlacement getPlacement();

	/**
	 * Returns the step of the journey that waits right now, such as the step whose prompt the player has to answer.
	 *
	 * @return the waiting step, or empty while nothing of the journey waits
	 */
	@NotNull Optional<CheckpointRequest> getWaiting();

	/**
	 * Runs a command as the held player, the way providers take answers, such as a provider's login command with the
	 * password the player entered.
	 *
	 * @param command the command line, without a leading slash
	 * @return whether the platform ran a command
	 */
	@NotNull CompletionStage<Boolean> answer(@NotNull String command);

	/**
	 * Hands the connection over to the step's server, for a step this checkpoint cannot ask the player about: the
	 * hold ends, routing sends the player to the server of the step that waits, and the player answers there in
	 * chat. Later steps of the journey wait there too.
	 */
	void handOver();

	/**
	 * Completes once the hold has ended: the journey finished and the player goes on to where it completes, the
	 * connection was handed over, or the player left.
	 *
	 * @return the end of the hold
	 */
	@NotNull CompletionStage<Void> released();
}
