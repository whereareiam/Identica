package me.whereareiam.identica.addon.dialogs.platform.velocity;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.addon.dialogs.display.packetevents.PacketEventsDialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Dialogs on Velocity, whose API cannot show them, so PacketEvents always does; a command button's command runs
 * through Velocity's command manager.
 */
public final class VelocityDialogPlatform implements DialogPlatform {
	private final ProxyServer proxy;
	private final Object plugin;

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
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		return proxy.getPlayer(connectionUniqueId)
				.map(Player::getProtocolVersion)
				.filter(version -> version.noLessThan(ProtocolVersion.MINECRAFT_1_21_6))
				.isPresent();
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
