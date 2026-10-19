package me.whereareiam.identica.addon.dialogs.platform.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.addon.dialogs.display.packetevents.PacketEventsDialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialogs on Velocity, whose API cannot show them, so PacketEvents always does; a command button's command runs
 * through Velocity's command manager. It remembers the client version of a player who logs in, since Identica asks
 * whether the client shows dialogs before Velocity lists the player.
 */
public final class VelocityDialogPlatform implements DialogPlatform {
	private final ProxyServer proxy;
	private final Object plugin;
	private final Map<UUID, ProtocolVersion> loggingIn = new ConcurrentHashMap<>();

	/**
	 * Creates the platform.
	 *
	 * @param proxy the proxy
	 * @param plugin the addon's plugin instance, which Velocity's event listeners belong to
	 */
	public VelocityDialogPlatform(@NotNull ProxyServer proxy, @NotNull Object plugin) {
		this.proxy = proxy;
		this.plugin = plugin;
	}

	@Override
	public void start(@NotNull DialogPlatform.Listener listener) {
		proxy.getEventManager().register(plugin, new VelocityConnectionListener(listener));
		proxy.getEventManager().register(plugin, this);
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		ProtocolVersion version = proxy.getPlayer(connectionUniqueId)
				.map(Player::getProtocolVersion)
				.orElse(loggingIn.get(connectionUniqueId));
		return version != null && version.noLessThan(ProtocolVersion.MINECRAFT_1_21_6);
	}

	/**
	 * Remembers the client version of a player who logs in, before Identica's journey of the login runs.
	 */
	@Subscribe(order = PostOrder.FIRST)
	public void onLogin(LoginEvent event) {
		loggingIn.put(event.getPlayer().getUniqueId(), event.getPlayer().getProtocolVersion());
	}

	@Subscribe
	public void onPostLogin(PostLoginEvent event) {
		loggingIn.remove(event.getPlayer().getUniqueId());
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		loggingIn.remove(event.getPlayer().getUniqueId());
	}

	/**
	 * Returns PacketEvents, since Velocity has no dialog API of its own.
	 */
	@Override
	public @NotNull DialogDisplay display() {
		return new PacketEventsDialogDisplay(proxy::getPlayer);
	}

	@Override
	public void run(@NotNull UUID connectionUniqueId, @NotNull String command) {
		proxy.getPlayer(connectionUniqueId).ifPresent(online -> proxy.getCommandManager().executeAsync(online, command));
	}

	@Override
	public void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component reason) {
		proxy.getPlayer(connectionUniqueId).ifPresent(online -> online.disconnect(reason));
	}
}
