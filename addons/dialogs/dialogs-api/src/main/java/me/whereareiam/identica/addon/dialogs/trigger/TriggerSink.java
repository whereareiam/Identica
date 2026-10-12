package me.whereareiam.identica.addon.dialogs.trigger;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Where a {@link DialogTrigger} reports: a moment that may show a dialog, and the end of a moment, which closes the
 * dialog it showed.
 */
public interface TriggerSink {
	/**
	 * Reports a moment. The first rule that names the trigger and whose conditions all match picks the dialog to
	 * show; a matching rule without a dialog, or none matching, closes the dialog the trigger showed the player before.
	 *
	 * @param context the moment
	 */
	void fire(@NotNull TriggerContext context);

	/**
	 * Reports that the trigger's moment for a player is over, which closes the dialog it showed the player.
	 *
	 * @param connectionUniqueId the unique id of the player's connection on the proxy
	 * @param trigger the trigger's ID
	 */
	void settle(@NotNull UUID connectionUniqueId, @NotNull String trigger);
}
