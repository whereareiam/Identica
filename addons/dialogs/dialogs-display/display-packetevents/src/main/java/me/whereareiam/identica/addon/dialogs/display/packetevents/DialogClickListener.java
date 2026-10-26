package me.whereareiam.identica.addon.dialogs.display.packetevents;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCustomClickAction;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.presentation.DialogClicks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the clicks the addon's buttons send, such as a command button or the one that switches to chat, keeps them
 * from the server and reports them with the values they carry.
 */
final class DialogClickListener implements PacketListener {
	private final DialogPlatform.Listener listener;

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 */
	DialogClickListener(@NotNull DialogPlatform.Listener listener) {
		this.listener = listener;
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.CUSTOM_CLICK_ACTION) return;

		WrapperPlayClientCustomClickAction click = new WrapperPlayClientCustomClickAction(event);
		String id = click.getId().toString();
		if (!id.startsWith(DialogClicks.PREFIX)) return;

		event.setCancelled(true);
		UUID connectionUniqueId = event.getUser().getUUID();
		if (connectionUniqueId == null) return;

		listener.clicked(connectionUniqueId, id, values(click.getPayload()));
	}

	private static Map<String, String> values(NBT payload) {
		Map<String, String> values = new HashMap<>();
		if (!(payload instanceof NBTCompound compound)) return values;

		compound.getTags().forEach((key, tag) -> {
			if (tag instanceof NBTString text) values.put(key, text.getValue());
			if (tag instanceof NBTNumber number) values.put(key, String.valueOf(number.getAsNumber()));
		});
		return values;
	}
}
