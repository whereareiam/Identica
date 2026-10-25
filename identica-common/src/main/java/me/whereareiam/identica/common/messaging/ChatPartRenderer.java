package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.ChatPart;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a message as chat lines, with the message's text or the part's own.
 */
final class ChatPartRenderer implements PartRenderer<ChatPart> {
	@Override
	public @NotNull PartRendering render(@NotNull ChatPart part, @NotNull PlayerMessage message, @NotNull Identity recipient) {
		String text = part.getText() != null ? part.getText() : message.getText();
		if (!text.isBlank())
			recipient.sendMessage(Serializer.serialize(recipient, text, message.getPlaceholders()));

		return PartRendering.shown();
	}
}
