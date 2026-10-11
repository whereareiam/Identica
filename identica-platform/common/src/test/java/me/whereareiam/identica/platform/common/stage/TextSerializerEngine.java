package me.whereareiam.identica.platform.common.stage;

import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Serializer that renders a message as plain text, so a test can compare the component it expects.
 */
public final class TextSerializerEngine implements SerializerEngine {
	@Override
	public @NotNull String serialize(Component component) {
		return component.toString();
	}

	@Override
	public @NotNull Component serialize(SerializerContent content) {
		return Component.text(content.getMessage());
	}

	public @NotNull String renderTemplate(SerializerContent content) {
		return content.getMessage();
	}

	@Override
	public SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
		return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
	}
}
