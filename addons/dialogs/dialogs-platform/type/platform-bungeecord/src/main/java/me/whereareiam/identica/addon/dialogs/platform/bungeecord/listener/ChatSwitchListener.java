package me.whereareiam.identica.addon.dialogs.platform.bungeecord.listener;

import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import net.md_5.bungee.api.event.CustomClickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the button that switches a player to chat: a custom click the proxy keeps to itself instead of passing it
 * to the server.
 */
public final class ChatSwitchListener implements Listener {
	/**
	 * ID of the custom click the button sends.
	 */
	public static final String CHAT_CLICK = "identica:dialogs/chat";

	private final DialogPlatform.Listener listener;

	public ChatSwitchListener(@NotNull DialogPlatform.Listener listener) {
		this.listener = listener;
	}

	@EventHandler
	public void onCustomClick(CustomClickEvent event) {
		if (!CHAT_CLICK.equals(event.getId())) return;

		event.setCancelled(true);
		listener.switchedToChat(event.getPlayer().getUniqueId());
	}
}
