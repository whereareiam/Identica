package me.whereareiam.identica.addon.dialogs.platform.velocity.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCustomClickAction;
import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Reads the button that switches a player to chat: a custom click the proxy keeps to itself instead of passing it
 * to the server.
 */
public final class ChatSwitchListener implements PacketListener {
	/**
	 * ID of the custom click the button sends.
	 */
	public static final String CHAT_CLICK = "identica:dialogs/chat";

	private final PlatformListener listener;

	public ChatSwitchListener(@NotNull PlatformListener listener) {
		this.listener = listener;
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.CUSTOM_CLICK_ACTION) return;

		WrapperPlayClientCustomClickAction click = new WrapperPlayClientCustomClickAction(event);
		if (!click.getId().toString().equals(CHAT_CLICK)) return;

		event.setCancelled(true);
		UUID connectionUniqueId = event.getUser().getUUID();
		if (connectionUniqueId != null) listener.switchedToChat(connectionUniqueId);
	}
}
