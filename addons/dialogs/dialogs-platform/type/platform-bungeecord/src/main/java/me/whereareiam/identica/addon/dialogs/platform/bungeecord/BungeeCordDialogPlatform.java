package me.whereareiam.identica.addon.dialogs.platform.bungeecord;

import me.whereareiam.identica.addon.dialogs.display.bungeecord.BungeeCordDialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.protocol.ProtocolConstants;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Dialogs on BungeeCord, which shows them through its own dialog API; a command button's command runs through
 * BungeeCord's plugin manager as the player.
 */
public final class BungeeCordDialogPlatform implements DialogPlatform {
	private final Plugin plugin;

	/**
	 * Creates the platform.
	 *
	 * @param plugin the addon's plugin instance, which BungeeCord's listeners belong to
	 */
	public BungeeCordDialogPlatform(@NotNull Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void start(@NotNull DialogPlatform.Listener listener) {
		plugin.getProxy().getPluginManager().registerListener(plugin, new BungeeCordConnectionListener(listener));
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		ProxiedPlayer online = plugin.getProxy().getPlayer(connectionUniqueId);
		return online != null && online.getPendingConnection().getVersion() >= ProtocolConstants.MINECRAFT_1_21_6;
	}

	@Override
	public @NotNull DialogDisplay display() {
		return new BungeeCordDialogDisplay(plugin);
	}

	@Override
	public void run(@NotNull UUID connectionUniqueId, @NotNull String command) {
		ProxiedPlayer online = plugin.getProxy().getPlayer(connectionUniqueId);
		if (online != null) plugin.getProxy().getPluginManager().dispatchCommand(online, command);
	}

	@Override
	public void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component reason) {
		ProxiedPlayer online = plugin.getProxy().getPlayer(connectionUniqueId);
		if (online != null) online.disconnect(BungeeComponentSerializer.get().serialize(reason));
	}
}
