package me.whereareiam.identica.platform.adapter;

import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Platform adapter for {@link me.whereareiam.identica.checkpoint.Checkpoints}: where the platform can hold a
 * connection whose step waits, and how it runs a held player's answers.
 */
public interface PlatformCheckpointAdapter {
	/**
	 * Returns the placements the platform can hold for a connection, besides
	 * {@link CheckpointPlacement#STEP_SERVER}, which every platform holds. It may depend on the connection, such as on
	 * its client's version.
	 *
	 * @param connectionUniqueId the connection's unique id on the proxy
	 * @return the placements, without {@link CheckpointPlacement#STEP_SERVER}
	 */
	@NotNull Set<CheckpointPlacement> placements(@NotNull UUID connectionUniqueId);

	/**
	 * Runs a command as a connected player, the way the player would type it.
	 *
	 * @param connectionUniqueId the connection's unique id on the proxy
	 * @param command            the command line, without a leading slash
	 * @return whether the platform ran a command; {@code false} when the player is gone
	 */
	@NotNull CompletionStage<Boolean> answer(@NotNull UUID connectionUniqueId, @NotNull String command);
}
