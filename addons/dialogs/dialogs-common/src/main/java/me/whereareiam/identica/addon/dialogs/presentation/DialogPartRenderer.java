package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shows the dialog part of an Identica message: the dialog the part names, or the one the rules pick. A player whose
 * client cannot show dialogs, who switched to chat, or for whom no rule picks a dialog, is told the message in chat
 * instead, which the part type falls back to. Until a dialog reaches the player's client, in a server's world or on
 * the loading screen while the addon's checkpoint holds the player, it waits.
 */
public final class DialogPartRenderer implements PartRenderer<DialogPart> {
	private final DialogPresenter presenter;
	private final DialogChooser chooser;

	/**
	 * Creates the renderer.
	 *
	 * @param presenter shows the dialogs
	 * @param chooser picks the dialog for a part that names none
	 */
	public DialogPartRenderer(@NotNull DialogPresenter presenter, @NotNull DialogChooser chooser) {
		this.presenter = presenter;
		this.chooser = chooser;
	}

	@Override
	public @NotNull PartRendering render(@NotNull DialogPart part, @NotNull PlayerMessage message, @NotNull Identity recipient) {
		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId == null || !presenter.shows(connectionUniqueId)) return PartRendering.unsupported();

		String dialog = part.dialog() != null
				? chooser.existing(message, part.dialog())
				: chooser.choose(message, recipient, connectionUniqueId);
		if (dialog == null) return PartRendering.unsupported();
		if (!presenter.reaches(connectionUniqueId)) return PartRendering.notYet();

		DialogPresenter.Wanted shown = presenter.want(connectionUniqueId, dialog, message);
		return PartRendering.shown(() -> presenter.release(connectionUniqueId, shown));
	}
}
