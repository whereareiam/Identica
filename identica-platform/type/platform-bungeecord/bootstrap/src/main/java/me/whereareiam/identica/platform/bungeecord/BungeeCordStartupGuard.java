package me.whereareiam.identica.platform.bungeecord;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Refuses every connection until Identica has finished its startup.
 * <p>
 * A proxy keeps running when a plugin fails to enable. Without this guard, players would then join
 * the default server without passing any authentication pipeline. The guard depends only on classes the
 * proxy provides, so it also works when the runtime libraries or the configuration cannot be loaded.
 */
public class BungeeCordStartupGuard implements Listener {
	/**
	 * The client's own text for unavailable authentication, shown in each player's language. Identica's messages
	 * cannot be used here: the configuration may be what failed to load.
	 */
	static final String REASON_KEY = "multiplayer.disconnect.authservers_down";

	private volatile boolean ready;

	/**
	 * Lets connections through to the regular listeners. Called once startup has completed.
	 */
	public void open() {
		ready = true;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreLogin(PreLoginEvent event) {
		if (ready) return;
		event.setCancelled(true);
		event.setReason(reason());
	}

	// Runs last, so a listener that allowed the connection again cannot let it through.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onLogin(LoginEvent event) {
		if (ready) return;
		event.setCancelled(true);
		event.setReason(reason());
	}

	private static BaseComponent reason() {
		return new TranslatableComponent(REASON_KEY);
	}
}
