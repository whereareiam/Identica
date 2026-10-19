package me.whereareiam.identica.addon.dialogs.display.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerClearDialog;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerShowDialog;
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
 * the addon's buttons. A client still configuring, such as one Identica holds on the loading screen, gets the
 * configuration phase's packets, and one in a server's world those of the game. The proxy only says which of its
 * players a connection belongs to, since PacketEvents sends to the proxy's own player objects.
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
		players.apply(connectionUniqueId).ifPresent(online -> {
			com.github.retrooper.packetevents.protocol.dialog.Dialog shown = PacketEventsDialogMapper.of(dialog, text);
			send(online, configuring(online) ? new WrapperConfigServerShowDialog(shown) : new WrapperPlayServerShowDialog(shown));
		});
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		players.apply(connectionUniqueId).ifPresent(online ->
				send(online, configuring(online) ? new WrapperConfigServerClearDialog() : new WrapperPlayServerClearDialog()));
	}

	private static boolean configuring(Object online) {
		User user = PacketEvents.getAPI().getPlayerManager().getUser(online);
		return user != null && user.getEncoderState() == ConnectionState.CONFIGURATION;
	}

	private static void send(Object online, PacketWrapper<?> packet) {
		PacketEvents.getAPI().getPlayerManager().sendPacket(online, packet);
	}
}
