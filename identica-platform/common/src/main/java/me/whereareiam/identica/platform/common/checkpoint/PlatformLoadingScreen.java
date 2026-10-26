package me.whereareiam.identica.platform.common.checkpoint;

import com.google.inject.Singleton;
import me.whereareiam.identica.message.LoadingScreen;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players a platform holds on the loading screen, where the client shows no chat, titles or bars. Message parts
 * that need a server's world wait while a player is held and are shown once the player's client joined a server.
 */
@Singleton
public class PlatformLoadingScreen implements LoadingScreen {
	private final Set<UUID> held = ConcurrentHashMap.newKeySet();

	/**
	 * Records that the platform now holds a player on the loading screen.
	 *
	 * @param connectionUniqueId the held player's connection
	 */
	public void held(@NotNull UUID connectionUniqueId) {
		held.add(connectionUniqueId);
	}

	/**
	 * Records that the platform no longer holds a player on the loading screen.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void released(@NotNull UUID connectionUniqueId) {
		held.remove(connectionUniqueId);
	}

	@Override
	public boolean holds(@NotNull UUID connectionUniqueId) {
		return held.contains(connectionUniqueId);
	}
}
