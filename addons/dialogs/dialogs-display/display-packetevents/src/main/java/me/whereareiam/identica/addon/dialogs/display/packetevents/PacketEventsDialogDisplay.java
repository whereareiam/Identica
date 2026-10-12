package me.whereareiam.identica.addon.dialogs.display.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerClearDialog;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Dialogs through PacketEvents, on any proxy it runs on: it sends the show and clear packets and reads the clicks of
 * the addon's buttons. The proxy only says which of its players a connection belongs to, since PacketEvents sends to
 * the proxy's own player objects.
 */
public final class PacketEventsDialogDisplay implements DialogDisplay {
	private final Function<UUID, Optional<?>> players;

	/**
	 * Creates the display.
	 *
	 * @param players finds the proxy's player of a connection, or empty when the player is not online
	 */
	public PacketEventsDialogDisplay(@NotNull Function<UUID, Optional<?>> players) {
		this.players = players;
	}

	@Override
	public void start(@NotNull DialogPlatform.Listener listener) {
		PacketEvents.getAPI().getEventManager().registerListener(new DialogClickListener(listener), PacketListenerPriority.NORMAL);
	}

	@Override
	public void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text) {
		players.apply(connectionUniqueId).ifPresent(online ->
				PacketEvents.getAPI().getPlayerManager().sendPacket(online, new WrapperPlayServerShowDialog(PacketEventsDialogMapper.of(dialog, text))));
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		players.apply(connectionUniqueId).ifPresent(online ->
				PacketEvents.getAPI().getPlayerManager().sendPacket(online, new WrapperPlayServerClearDialog()));
	}
}
