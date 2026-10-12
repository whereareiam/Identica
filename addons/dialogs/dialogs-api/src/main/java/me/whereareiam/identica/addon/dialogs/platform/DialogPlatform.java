package me.whereareiam.identica.addon.dialogs.platform;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shows dialogs on one proxy. Each proxy the addon runs on implements it with what that proxy offers, and reports
 * the players' side to a {@link PlatformListener}.
 */
public interface DialogPlatform {
	/**
	 * Starts reporting to the listener.
	 *
	 * @param listener where the platform reports
	 */
	void start(@NotNull PlatformListener listener);

	/**
	 * Returns whether a dialog shown to the player now reaches the client: the player has finished connecting to a
	 * server, after which the platform reports {@link PlatformListener#connected(UUID)}, and their client has
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
}
