package me.whereareiam.identica.addon.dialogs.display.bungeecord;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Dialogs through BungeeCord's own dialog API, which needs no other plugin.
 */
public final class BungeeCordDialogDisplay implements DialogDisplay {
	private final Plugin plugin;

	/**
	 * Creates the display.
	 *
	 * @param plugin the addon's plugin instance, which BungeeCord's listeners belong to
	 */
	public BungeeCordDialogDisplay(@NotNull Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void start(@NotNull DialogPlatform.Listener listener) {
		plugin.getProxy().getPluginManager().registerListener(plugin, new DialogClickListener(listener));
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
