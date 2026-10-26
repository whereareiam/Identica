package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.PartRenderer;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.message.ClientScreen;
import me.whereareiam.identica.model.message.TitlePart;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shows a message's title and subtitle, or the part's own, on the player's screen. Until the client is in a server's
 * world, such as on the loading screen or between servers, the title waits.
 */
final class TitlePartRenderer implements PartRenderer<TitlePart> {
	private final MessageService messages;

	TitlePartRenderer(@NotNull MessageService messages) {
		this.messages = messages;
	}

	@Override
	public @NotNull PartRendering render(@NotNull TitlePart part, @NotNull PlayerMessage message, @NotNull Identity recipient) {
		UUID connectionUniqueId = recipient.getConnectionUniqueId();
		if (connectionUniqueId != null && messages.screen(connectionUniqueId) != ClientScreen.WORLD) return PartRendering.notYet();

		String title = part.getTitle() != null ? part.getTitle() : message.getTitle();
		String subtitle = part.getSubtitle() != null ? part.getSubtitle() : message.getSubtitle();
		if (isBlank(title) && isBlank(subtitle)) return PartRendering.shown();

		recipient.sendTitle(Title.title(
				component(recipient, title, message),
				component(recipient, subtitle, message)
		));
		return PartRendering.shown();
	}

	private static @NotNull Component component(@NotNull Identity recipient, @Nullable String text, @NotNull PlayerMessage message) {
		if (isBlank(text)) return Component.empty();

		return Serializer.serialize(recipient, text, message.getPlaceholders());
	}

	private static boolean isBlank(@Nullable String text) {
		return text == null || text.isBlank();
	}
}
