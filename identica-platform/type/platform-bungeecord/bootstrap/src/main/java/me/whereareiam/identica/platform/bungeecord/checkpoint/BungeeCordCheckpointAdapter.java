package me.whereareiam.identica.platform.bungeecord.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * BungeeCord holds a connection on the loading screen in the configuration of the first server it connects the player
 * to: it waits with the configuration that server started, which clients from Minecraft 1.20.2 on have. The server
 * keeps the client connected meanwhile.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordCheckpointAdapter implements PlatformCheckpointAdapter {
	/**
	 * Protocol version of Minecraft 1.20.2, the first with a configuration phase.
	 */
	static final int CONFIGURATION_PROTOCOL = 764;

	private final ProxyServer proxy;

	@Override
	public @NotNull Set<CheckpointPlacement> placements(@NotNull UUID connectionUniqueId) {
		ProxiedPlayer player = proxy.getPlayer(connectionUniqueId);
		if (player == null || player.getPendingConnection().getVersion() < CONFIGURATION_PROTOCOL) return Set.of();

		return Set.of(CheckpointPlacement.LOADING_SCREEN);
	}

	@Override
	public @NotNull CompletionStage<Boolean> answer(@NotNull UUID connectionUniqueId, @NotNull String command) {
		ProxiedPlayer player = proxy.getPlayer(connectionUniqueId);
		if (player == null) return CompletableFuture.completedFuture(false);

		return CompletableFuture.completedFuture(proxy.getPluginManager().dispatchCommand(player, command));
	}
}
