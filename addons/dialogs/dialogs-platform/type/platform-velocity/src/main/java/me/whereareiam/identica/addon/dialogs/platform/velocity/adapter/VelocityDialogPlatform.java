package me.whereareiam.identica.addon.dialogs.platform.velocity.adapter;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerClearDialog;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.ChatSwitchListener;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.VelocityConnectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Dialogs on Velocity, whose API cannot show them: PacketEvents sends the show and clear packets and reads the
 * button that switches to chat.
 */
public final class VelocityDialogPlatform implements DialogPlatform {
	private final ProxyServer proxy;
	private final Object plugin;
	private volatile @Nullable VelocityConnectionListener connections;

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
		VelocityConnectionListener started = new VelocityConnectionListener(listener);
		proxy.getEventManager().register(plugin, started);
		connections = started;
		PacketEvents.getAPI().getEventManager().registerListener(new ChatSwitchListener(listener), PacketListenerPriority.NORMAL);
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		VelocityConnectionListener current = connections;
		if (current == null || !current.hasArrived(connectionUniqueId)) return false;

		return proxy.getPlayer(connectionUniqueId)
				.map(Player::getProtocolVersion)
				.filter(version -> version.noLessThan(ProtocolVersion.MINECRAFT_1_21_6))
				.isPresent();
	}

	@Override
	public void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text) {
		proxy.getPlayer(connectionUniqueId).ifPresent(online ->
				PacketEvents.getAPI().getPlayerManager().sendPacket(online, new WrapperPlayServerShowDialog(PacketEventsDialogMapper.of(dialog, text))));
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		proxy.getPlayer(connectionUniqueId).ifPresent(online -> PacketEvents.getAPI().getPlayerManager().sendPacket(online, new WrapperPlayServerClearDialog()));
	}
}
