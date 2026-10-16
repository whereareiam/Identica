package me.whereareiam.identica.platform.velocity.checkpoint;

import com.google.inject.Singleton;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Velocity holds no connection outside the step's server yet.
 */
@Singleton
public class VelocityCheckpointAdapter implements PlatformCheckpointAdapter {
	@Override
	public @NotNull Set<CheckpointPlacement> placements() {
		return Set.of();
	}
}
