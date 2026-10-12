package me.whereareiam.identica.addon.dialogs.platform;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Gets dialogs onto players' clients and reads the clicks of the addon's buttons back: through the proxy's own dialog
 * API or through PacketEvents. A {@link DialogPlatform} picks the display its proxy uses when the addon starts.
 */
public interface DialogDisplay {
	/**
	 * Starts reading the clicks of the addon's buttons, which it keeps from the server and reports to
	 * {@link DialogPlatform.Listener#clicked}.
	 *
	 * @param listener where the display reports
	 */
	void start(@NotNull DialogPlatform.Listener listener);

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
