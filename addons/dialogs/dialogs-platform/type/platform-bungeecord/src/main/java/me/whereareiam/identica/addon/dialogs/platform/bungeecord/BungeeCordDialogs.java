package me.whereareiam.identica.addon.dialogs.platform.bungeecord;

import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.addon.dialogs.DialogsAddon;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * The dialogs addon on BungeeCord, whose API shows dialogs itself. It needs Identica and starts once Identica is
 * ready.
 */
public final class BungeeCordDialogs extends Plugin {
	private volatile @Nullable DialogsAddon addon;

	@Override
	public void onEnable() {
		IdenticaAPI.whenReady(identica -> addon = DialogsAddon.start(identica, new BungeeCordDialogPlatform(this), getDataFolder().toPath()));
	}

	@Override
	public void onDisable() {
		DialogsAddon running = addon;
		if (running != null) running.stop();
	}
}
