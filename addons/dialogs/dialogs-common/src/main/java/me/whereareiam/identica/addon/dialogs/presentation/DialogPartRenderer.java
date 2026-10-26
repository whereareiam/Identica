package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.message.ClientScreen;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shows the dialog part of an Identica message: the dialog the part names, or the one the rules pick. A player whose
 * client cannot show dialogs, who switched to chat, or for whom no rule picks a dialog, is told the message in chat
 * instead, which the part type falls back to. Until the player's client is in a server's world, the dialog waits.
 */
public final class DialogPartRenderer implements PartRenderer<DialogPart> {
	private final DialogPresenter presenter;
	private final DialogChooser chooser;
	private final MessageService messages;

	/**
	 * Creates the renderer.
	 *
	 * @param presenter shows the dialogs
	 * @param chooser picks the dialog for a part that names none
	 * @param messages tells whether the player's client is in a server's world
	 */
	public DialogPartRenderer(@NotNull DialogPresenter presenter, @NotNull DialogChooser chooser, @NotNull MessageService messages) {
		this.presenter = presenter;
		this.chooser = chooser;
		this.messages = messages;
	}

	@Override
	public @NotNull PartRendering render(@NotNull DialogPart part, @NotNull PlayerMessage message, @NotNull Identity recipient) {
		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId == null || !presenter.shows(connectionUniqueId)) return PartRendering.unsupported();

		String dialog = part.dialog() != null
				? chooser.existing(message, part.dialog())
				: chooser.choose(message, recipient, connectionUniqueId);
		if (dialog == null) return PartRendering.unsupported();
		if (messages.screen(connectionUniqueId) != ClientScreen.WORLD) return PartRendering.notYet();

		DialogPresenter.Wanted shown = presenter.want(connectionUniqueId, dialog, message);
		return PartRendering.shown(() -> presenter.release(connectionUniqueId, shown));
	}
}
