package me.whereareiam.identica.platform.velocity.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity holds a connection on the loading screen on the proxy: it waits for the event that chooses the player's
 * first server, when the client is already in its configuration phase, with no server connected. The proxy keeps the
 * client connected with cookie requests, which clients from Minecraft 1.20.5 on answer, so only they are held there.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityCheckpointAdapter implements PlatformCheckpointAdapter {
	private final ProxyServer proxy;
	private final Map<UUID, ProtocolVersion> versions = new ConcurrentHashMap<>();

	/**
	 * Remembers the client version of a player who is logging in, before their journey runs; the player is not yet
	 * known to the proxy then.
	 *
	 * @param player the player logging in
	 */
	public void connecting(@NotNull Player player) {
		versions.put(player.getUniqueId(), player.getProtocolVersion());
	}

	/**
	 * Forgets a player who left.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void left(@NotNull UUID connectionUniqueId) {
		versions.remove(connectionUniqueId);
	}

	@Override
	public @NotNull Set<CheckpointPlacement> placements(@NotNull UUID connectionUniqueId) {
		ProtocolVersion version = versions.get(connectionUniqueId);
		if (version == null || version.lessThan(ProtocolVersion.MINECRAFT_1_20_5)) return Set.of();

		return Set.of(CheckpointPlacement.LOADING_SCREEN);
	}

	@Override
	public @NotNull CompletionStage<Boolean> answer(@NotNull UUID connectionUniqueId, @NotNull String command) {
		Player player = proxy.getPlayer(connectionUniqueId).orElse(null);
		if (player == null) return CompletableFuture.completedFuture(false);

		return proxy.getCommandManager().executeAsync(player, command);
	}
}
