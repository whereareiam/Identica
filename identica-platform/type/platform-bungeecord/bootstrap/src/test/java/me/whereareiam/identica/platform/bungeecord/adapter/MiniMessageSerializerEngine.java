package me.whereareiam.identica.platform.bungeecord.adapter;

import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Serializer that parses messages as MiniMessage, so a test sees whether a platform passes the markup on.
 */
public final class MiniMessageSerializerEngine implements SerializerEngine {
	@Override
	public @NotNull String serialize(Component component) {
		return MiniMessage.miniMessage().serialize(component);
	}

	@Override
	public @NotNull Component serialize(SerializerContent content) {
		return MiniMessage.miniMessage().deserialize(content.getMessage());
	}

	public @NotNull String renderTemplate(SerializerContent content) {
		return content.getMessage();
	}

	@Override
	public SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
		return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
	}
}
