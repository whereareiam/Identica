package me.whereareiam.identica.addon.dialogs.platform.velocity.adapter;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerClearDialog;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerShowDialog;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerClearDialog;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.platform.velocity.checkpoint.LoadingScreenCheckpoint;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.ChatSwitchListener;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.LoadingScreenAnswerListener;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.VelocityConnectionListener;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.checkpoint.Checkpoints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dialogs on Velocity, whose API cannot show them: PacketEvents sends the show and clear packets and reads the
 * buttons whose clicks the proxy keeps to itself. A player Identica holds on the loading screen, through the
 * {@link LoadingScreenCheckpoint}, gets the configuration phase's packets, and the proxy runs their answers.
 */
public final class VelocityDialogPlatform implements DialogPlatform {
	private final ProxyServer proxy;
	private final Object plugin;
	private final Checkpoints checkpoints;
	private final LoadingScreenCheckpoint loadingScreen = new LoadingScreenCheckpoint();
	private final LoadingScreenAnswerListener answers = new LoadingScreenAnswerListener(loadingScreen);
	private volatile @Nullable VelocityConnectionListener connections;

	/**
	 * Creates the platform.
	 *
	 * @param proxy the proxy
	 * @param plugin the addon's plugin instance, which Velocity's event listeners belong to
	 * @param checkpoints Identica's checkpoints, which the loading screen is registered with
	 */
	public VelocityDialogPlatform(@NotNull ProxyServer proxy, @NotNull Object plugin, @NotNull Checkpoints checkpoints) {
		this.proxy = proxy;
		this.plugin = plugin;
		this.checkpoints = checkpoints;
	}

	@Override
	public void start(@NotNull DialogPlatform.Listener listener) {
		VelocityConnectionListener started = new VelocityConnectionListener(listener);
		proxy.getEventManager().register(plugin, started);
		connections = started;
		PacketEvents.getAPI().getEventManager().registerListener(new ChatSwitchListener(listener), PacketListenerPriority.NORMAL);

		loadingScreen.start(listener);
		proxy.getEventManager().register(plugin, loadingScreen);
		PacketEvents.getAPI().getEventManager().registerListener(answers, PacketListenerPriority.NORMAL);
		checkpoints.register(loadingScreen);
	}

	/**
	 * Stops asking players on the loading screen. Players held there already finish their journey there.
	 */
	public void stop() {
		checkpoints.unregister(loadingScreen);
	}

	@Override
	public boolean supports(@NotNull UUID connectionUniqueId) {
		if (onLoadingScreen(connectionUniqueId)) return true;

		VelocityConnectionListener current = connections;
		if (current == null || !current.hasArrived(connectionUniqueId)) return false;

		return proxy.getPlayer(connectionUniqueId)
				.map(Player::getProtocolVersion)
				.filter(version -> version.noLessThan(ProtocolVersion.MINECRAFT_1_21_6))
				.isPresent();
	}

	@Override
	public void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text) {
		Player player = proxy.getPlayer(connectionUniqueId).orElse(null);
		if (player == null) return;

		if (onLoadingScreen(connectionUniqueId)) {
			answers.shown(connectionUniqueId, commands(dialog, text));
			PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperConfigServerShowDialog(PacketEventsDialogMapper.of(dialog, text, true)));
			return;
		}

		answers.forget(connectionUniqueId);
		PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerShowDialog(PacketEventsDialogMapper.of(dialog, text)));
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		Player player = proxy.getPlayer(connectionUniqueId).orElse(null);
		if (player == null) return;

		answers.forget(connectionUniqueId);
		if (onLoadingScreen(connectionUniqueId)) {
			PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperConfigServerClearDialog());
			return;
		}

		PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerClearDialog());
	}

	/**
	 * Sends a player on the loading screen to the step's server, where they answer in chat.
	 */
	@Override
	public void unasked(@NotNull UUID connectionUniqueId) {
		loadingScreen.held(connectionUniqueId).ifPresent(CheckpointStop::handOver);
	}

	private boolean onLoadingScreen(UUID connectionUniqueId) {
		return loadingScreen.held(connectionUniqueId).isPresent()
				&& proxy.getPlayer(connectionUniqueId).map(Player::getProtocolState).orElse(null) == ProtocolState.CONFIGURATION;
	}

	/**
	 * Returns the command line of each button, by its index in the dialog: the actions, then the exit button.
	 */
	private static List<String> commands(Dialog dialog, DialogText text) {
		List<String> lines = new ArrayList<>();
		for (DialogAction action : dialog.getActions())
			lines.add(text.command(action));
		if (dialog.getExitAction() != null) lines.add(text.command(dialog.getExitAction()));
		return lines;
	}
}
