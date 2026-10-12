package me.whereareiam.identica.addon.dialogs.platform;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * The addon's view of one proxy: which players can see dialogs, running their commands, and how dialogs reach their
 * clients, its {@link DialogDisplay}. Each proxy the addon runs on implements it with what that proxy offers, and
 * reports the players' side to a {@link Listener}.
 */
public interface DialogPlatform {
	/**
	 * Starts reporting to the listener.
	 *
	 * @param listener where the platform reports
	 */
	void start(@NotNull Listener listener);

	/**
	 * Returns whether the player's client can show dialogs: the player is online on the proxy and their client is
	 * Minecraft 1.21.6 or newer, which brought dialogs. Whether the client is in a server's world, where a dialog
	 * reaches it, comes from Identica's {@code ServerJoinedEvent}, not from the platform.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @return whether the player's client can show dialogs
	 */
	boolean supports(@NotNull UUID connectionUniqueId);

	/**
	 * Returns how dialogs reach players' clients on this proxy: through its own dialog API where it has one, and
	 * through PacketEvents otherwise. The addon asks once, when it starts.
	 *
	 * @return the display the proxy uses
	 */
	@NotNull DialogDisplay display();

	/**
	 * Runs a command on the proxy as the player, as if they typed it. A dialog's command button runs its command this
	 * way, so the client never has to know the command, which a client cannot check for every proxy.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param command the command line, without its leading slash
	 */
	void run(@NotNull UUID connectionUniqueId, @NotNull String command);

	/**
	 * Disconnects the player from the proxy. A dialog's disconnect button leaves the server this way.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param reason the reason the player's client shows
	 */
	void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component reason);

	/**
	 * What a {@link DialogPlatform} and its {@link DialogDisplay} report about players.
	 */
	interface Listener {
		/**
		 * Reports that a player's client leaves the world of its server to connect to another one. Until Identica
		 * reports it in the new server's world, a dialog would not reach it, and the switch closes every dialog it
		 * has open.
		 *
		 * @param connectionUniqueId the unique id of the player's connection on the proxy
		 */
		void switchingServer(@NotNull UUID connectionUniqueId);

		/**
		 * Reports a button the player pressed that sends a click to the proxy instead of acting on the client, such as
		 * a command button, a disconnect button or the one that switches to chat. The display passes on only the clicks of the addon's
		 * dialogs and keeps them from the server.
		 *
		 * @param connectionUniqueId the unique id of the player's connection on the proxy
		 * @param click the click's ID
		 * @param values what the click carries, by key: the button's own values and the dialog's inputs
		 */
		void clicked(@NotNull UUID connectionUniqueId, @NotNull String click, @NotNull Map<String, String> values);

		/**
		 * Reports that a player left the proxy.
		 *
		 * @param connectionUniqueId the unique id of the player's connection on the proxy
		 */
		void left(@NotNull UUID connectionUniqueId);
	}
}
