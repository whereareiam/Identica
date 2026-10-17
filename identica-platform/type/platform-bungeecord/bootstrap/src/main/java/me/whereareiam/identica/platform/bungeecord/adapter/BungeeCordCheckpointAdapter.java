package me.whereareiam.identica.platform.bungeecord.adapter;

import com.google.inject.Singleton;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * BungeeCord holds no connection outside the step's server: before the first server its client is still logging in,
 * where nothing can be shown, and its configuration hold needs a server connected.
 */
@Singleton
public class BungeeCordCheckpointAdapter implements PlatformCheckpointAdapter {
	@Override
	public @NotNull Set<CheckpointPlacement> placements() {
		return Set.of();
	}
}
