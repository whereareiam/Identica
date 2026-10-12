package me.whereareiam.identica.addon.dialogs.platform.bungeecord.adapter;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import me.whereareiam.identica.addon.dialogs.platform.bungeecord.listener.BungeeCordConnectionListener;
import me.whereareiam.identica.addon.dialogs.platform.bungeecord.listener.ChatSwitchListener;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.protocol.ProtocolConstants;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialogs on BungeeCord, through its own dialog API.
 */
public final class BungeeCordDialogPlatform implements DialogPlatform {
	private final Plugin plugin;
	private final Set<UUID> arrived = ConcurrentHashMap.newKeySet();

	/**
	 * Creates the platform.
	 *
	 * @param plugin the addon's plugin instance, which BungeeCord's listeners belong to
	 */
	public BungeeCordDialogPlatform(@NotNull Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void start(@NotNull PlatformListener listener) {
		plugin.getProxy().getPluginManager().registerListener(plugin, new BungeeCordConnectionListener(listener, arrived));
		plugin.getProxy().getPluginManager().registerListener(plugin, new ChatSwitchListener(listener));
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		if (!arrived.contains(connectionUniqueId)) return false;

		ProxiedPlayer online = plugin.getProxy().getPlayer(connectionUniqueId);
		return online != null && online.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_21_6;
	}

	@Override
	public void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text) {
		ProxiedPlayer online = plugin.getProxy().getPlayer(connectionUniqueId);
		if (online != null) online.showDialog(BungeeCordDialogMapper.of(dialog, text));
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		ProxiedPlayer online = plugin.getProxy().getPlayer(connectionUniqueId);
		if (online != null) online.clearDialog();
	}
}
