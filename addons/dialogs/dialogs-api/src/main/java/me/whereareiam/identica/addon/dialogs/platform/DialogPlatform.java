package me.whereareiam.identica.addon.dialogs.platform;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shows dialogs on one proxy. Each proxy the addon runs on implements it with what that proxy offers, and reports
 * the players' side to a {@link Listener}.
 */
public interface DialogPlatform {
	/**
	 * Starts reporting to the listener.
	 *
	 * @param listener where the platform reports
	 */
	void start(@NotNull Listener listener);

	/**
	 * Returns whether a dialog shown to the player now reaches the client: the player has finished connecting to a
	 * server, after which the platform reports {@link Listener#connected(UUID)}, and their client has
	 * dialogs, which arrived in Minecraft 1.21.6.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @return whether the player can be shown a dialog now
	 */
	boolean supports(@NotNull UUID connectionUniqueId);

	/**
	 * Shows a dialog, which replaces any dialog the player has open.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param dialog the dialog
	 * @param text renders the dialog's text for the player
	 */
	void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text);

	/**
	 * Closes the dialog the player has open, if any.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 */
	void clear(@NotNull UUID connectionUniqueId);

	/**
	 * What a {@link DialogPlatform} reports about players.
	 */
	interface Listener {
		/**
		 * Reports that a player connected to a server, after which a dialog reaches their client. A server switch
		 * closes every dialog on the client, so a dialog the player should see is shown again.
		 *
		 * @param connectionUniqueId the unique id of the player's connection on the proxy
		 */
		void connected(@NotNull UUID connectionUniqueId);

		/**
		 * Reports that a player pressed a button that switches them to chat: the addon closes the dialog and shows the
		 * player no more dialogs until they reconnect.
		 *
		 * @param connectionUniqueId the unique id of the player's connection on the proxy
		 */
		void switchedToChat(@NotNull UUID connectionUniqueId);

		/**
		 * Reports that a player left the proxy.
		 *
		 * @param connectionUniqueId the unique id of the player's connection on the proxy
		 */
		void left(@NotNull UUID connectionUniqueId);
	}
}
