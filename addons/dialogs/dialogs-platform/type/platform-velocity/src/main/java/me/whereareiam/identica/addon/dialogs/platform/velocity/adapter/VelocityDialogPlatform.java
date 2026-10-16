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
import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.ChatSwitchListener;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.VelocityConnectionListener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialogs on Velocity, whose API cannot show them: PacketEvents sends the show and clear packets and reads the
 * button that switches to chat.
 */
public final class VelocityDialogPlatform implements DialogPlatform {
	private final ProxyServer proxy;
	private final Object plugin;
	private final Set<UUID> arrived = ConcurrentHashMap.newKeySet();

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
	public void start(@NotNull PlatformListener listener) {
		proxy.getEventManager().register(plugin, new VelocityConnectionListener(listener, arrived));
		PacketEvents.getAPI().getEventManager().registerListener(new ChatSwitchListener(listener), PacketListenerPriority.NORMAL);
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		if (!arrived.contains(connectionUniqueId)) return false;

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
