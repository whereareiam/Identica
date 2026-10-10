package me.whereareiam.identica.platform.bungeecord.adapter.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.platform.adapter.PlatformForcedHostAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BungeeCord keeps forced hosts per listener, so the listener the player joined through decides.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordForcedHostAdapter implements PlatformForcedHostAdapter {
	private final ProxyServer proxyServer;

	@Override
	public @NotNull Optional<String> resolve(@NotNull ConnectionIdentity connection) {
		ConnectionIdentity.Origin origin = connection.getOrigin();
		if (origin == null) return Optional.empty();

		for (ListenerInfo listener : listeners(connection.getConnectionUniqueId())) {
			for (Map.Entry<String, String> forced : listener.getForcedHosts().entrySet()) {
				if (forced.getKey().equalsIgnoreCase(origin.getHost())) return Optional.of(forced.getValue());
			}
		}

		return Optional.empty();
	}

	/**
	 * A connection that is not a player yet does not expose its listener; every listener is then considered, in
	 * the order the proxy lists them.
	 */
	@SuppressWarnings("deprecation")
	private @NotNull Collection<ListenerInfo> listeners(@Nullable UUID connectionUniqueId) {
		ProxiedPlayer player = connectionUniqueId != null ? proxyServer.getPlayer(connectionUniqueId) : null;
		if (player != null) return List.of(player.getPendingConnection().getListener());

		return proxyServer.getConfig().getListeners();
	}
}
