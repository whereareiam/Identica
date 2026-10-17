package me.whereareiam.identica.platform.adapter;

import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Platform adapter that tells where the platform can hold a connection whose step waits, besides the step's server,
 * which every platform supports.
 */
public interface PlatformCheckpointAdapter {
	/**
	 * Returns the placements the platform can hold. {@link me.whereareiam.identica.checkpoint.Checkpoints} never picks
	 * a checkpoint whose placement is missing here.
	 *
	 * @return the placements, without {@link CheckpointPlacement#STEP_SERVER}
	 */
	@NotNull Set<CheckpointPlacement> placements();
}
