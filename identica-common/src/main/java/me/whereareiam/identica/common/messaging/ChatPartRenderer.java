package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.ChatPart;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.message.ClientScreen;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shows a message as chat lines: its text, or the part's own, and the text of every message appended to it. Until the
 * client is in a server's world, such as on the loading screen or between servers, the chat waits.
 */
final class ChatPartRenderer implements PartRenderer<ChatPart> {
	private final MessageService messages;

	ChatPartRenderer(@NotNull MessageService messages) {
		this.messages = messages;
	}

	@Override
	public @NotNull PartRendering render(@NotNull ChatPart part, @NotNull PlayerMessage message, @NotNull Identity recipient) {
		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId != null && messages.screen(connectionUniqueId) != ClientScreen.WORLD) return PartRendering.notYet();

		PlayerMessage chat = part.getText() != null ? message.toBuilder().text(part.getText()).build() : message;
		if (chat.hasText()) recipient.sendMessage(Serializer.serialize(recipient, chat));

		return PartRendering.shown();
	}
}
