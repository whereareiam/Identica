package me.whereareiam.identica.checkpoint;

import me.whereareiam.identica.model.auth.ConnectionDecision;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A connection the platform holds at a {@link Checkpoint}, as the checkpoint sees it.
 */
public interface CheckpointStop {
	/**
	 * Returns the held connection's unique id on the proxy.
	 *
	 * @return the connection's unique id
	 */
	@NotNull UUID getConnectionUniqueId();

	/**
	 * Continues the connection's journey where it is held, as reaching the step's server does on the step-server
	 * path: steps that waited for the player to be online run, and a step that waits for input asks for it.
	 *
	 * @return the journey's decision
	 */
	@NotNull CompletionStage<ConnectionDecision> resume();

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
	 * Completes once the hold has ended: the journey finished and the player goes on to their first server, the
	 * connection was handed over, or the player left.
	 *
	 * @return the end of the hold
	 */
	@NotNull CompletionStage<Void> released();
}
