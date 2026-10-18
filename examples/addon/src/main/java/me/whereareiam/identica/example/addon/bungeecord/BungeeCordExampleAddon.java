package me.whereareiam.identica.example.addon.bungeecord;

import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.example.addon.checkpoint.ConsoleCheckpoints;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * The example addon on BungeeCord: it registers the example's checkpoints, which the console answers through
 * {@code /examplehold}. Its {@code bungee.yml} declares Identica as a dependency, so BungeeCord loads Identica first.
 */
public final class BungeeCordExampleAddon extends Plugin {
	@Override
	public void onEnable() {
		IdenticaAPI.whenReady(this::enable);
	}

	private void enable(IdenticaAPI identica) {
		ConsoleCheckpoints checkpoints = ConsoleCheckpoints.register(identica, getLogger()::info);
		getProxy().getPluginManager().registerCommand(this, new BungeeCordHoldCommand(checkpoints));
		getLogger().info("Example addon enabled");
	}
}
