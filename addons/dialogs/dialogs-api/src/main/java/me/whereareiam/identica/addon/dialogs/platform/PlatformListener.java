package me.whereareiam.identica.addon.dialogs.platform;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * What a {@link DialogPlatform} reports about players.
 */
public interface PlatformListener {
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
