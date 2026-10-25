package me.whereareiam.identica.common.messaging;

import com.google.inject.Provider;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.ChatGate;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.ChatPart;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a message as chat lines, with the message's text or the part's own. While the gate holds a player's chat back,
 * such as on the loading screen, the chat waits there and the message's other parts are shown as usual.
 */
final class ChatPartRenderer implements PartRenderer<ChatPart> {
	private final Provider<ChatGate> gate;

	ChatPartRenderer(@NotNull Provider<ChatGate> gate) {
		this.gate = gate;
	}

	@Override
	public @NotNull PartRendering render(@NotNull ChatPart part, @NotNull PlayerMessage message, @NotNull Identity recipient) {
		PlayerMessage chat = part.getText() != null ? message.toBuilder().text(part.getText()).build() : message;
		if (chat.getText().isBlank() || gate.get().hold(recipient, chat)) return PartRendering.shown();

		show(chat, recipient);
		return PartRendering.shown();
	}

	/**
	 * Shows a message's text in chat now.
	 *
	 * @param message the message as chat
	 * @param recipient the player
	 */
	void show(@NotNull PlayerMessage message, @NotNull Identity recipient) {
		if (!message.getText().isBlank())
			recipient.sendMessage(Serializer.serialize(recipient, message.getText(), message.getPlaceholders()));
	}
}
