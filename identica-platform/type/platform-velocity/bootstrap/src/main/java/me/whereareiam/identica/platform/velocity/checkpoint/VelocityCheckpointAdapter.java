package me.whereareiam.identica.platform.velocity.checkpoint;

import com.google.inject.Singleton;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Velocity holds a connection on the proxy: it waits for the event that chooses the player's first server, and a
 * 1.20.2+ client is already in its configuration phase then, with no server connected.
 */
@Singleton
public class VelocityCheckpointAdapter implements PlatformCheckpointAdapter {
	@Override
	public @NotNull Set<CheckpointPlacement> placements() {
		return Set.of(CheckpointPlacement.PROXY);
	}
}
