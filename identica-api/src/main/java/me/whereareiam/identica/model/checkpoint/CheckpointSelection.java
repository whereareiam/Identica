package me.whereareiam.identica.model.checkpoint;

import lombok.Value;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

/**
 * The checkpoint picked for a waiting connection, and where it holds the connection.
 */
@Value
public class CheckpointSelection {
	/**
	 * The picked checkpoint.
	 */
	@NotNull Checkpoint checkpoint;
	/**
	 * The first of the checkpoint's placements the platform can hold for the connection.
	 */
	@NotNull CheckpointPlacement placement;
}
