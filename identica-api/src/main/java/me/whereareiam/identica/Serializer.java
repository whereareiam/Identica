package me.whereareiam.identica;

import com.google.inject.Provider;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import me.whereareiam.keystone.template.message.MessageTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Static helper around {@link SerializerEngine} to simplify serialization invocations.
 */
@SuppressWarnings("unused")
public final class Serializer {
	public static final String SCOPE = "identica";
	private static volatile Provider<SerializerEngine> serializerProvider;

	/**
	 * Initializes the shared serializer engine provider used by the static helper methods.
	 *
	 * @param provider provider that supplies the active serializer engine
	 */
	public static void initialize(@NotNull Provider<SerializerEngine> provider) {
		serializerProvider = Objects.requireNonNull(provider, "provider");
	}

	/**
	 * Serializes a plain message using the default Identica serializer scope.
	 *
	 * @param message message to serialize
	 * @return serialized component
	 */
	@NotNull
	public static Component serialize(@NotNull String message) {
		return getEngine().serialize(SerializerContent.builder()
				.scope(SCOPE)
				.message(message)
				.build());
	}

	/**
	 * Serializes a message for a specific receiver using the default Identica serializer scope.
	 *
	 * @param actor receiver that provides audience context
	 * @param message message to serialize
	 * @return serialized component
	 */
	@NotNull
	public static Component serialize(@NotNull Actor actor, @NotNull String message) {
		return getEngine().serialize(SerializerContent.builder()
				.receiver(actor)
				.scope(SCOPE)
				.message(message)
				.build());
	}

	/**
	 * Serializes a message for a specific receiver with explicit placeholders.
	 *
	 * @param actor receiver that provides audience context
	 * @param message message to serialize
	 * @param placeholders placeholder values to resolve while serializing
	 * @return serialized component
	 */
	@NotNull
	public static Component serialize(@NotNull Actor actor, @NotNull String message, @NotNull Map<String, String> placeholders) {
		return getEngine().serialize(SerializerContent.builder()
				.receiver(actor)
				.scope(SCOPE)
				.message(message)
				.placeholders(placeholders)
				.build());
	}

	/**
	 * Serializes a whole message as text for a receiver: its own text and the text of each message appended to it,
	 * each with its own placeholders, on separate lines. A message without a receiver, such as the reason a connection
	 * is refused before it became a player, is serialized without one.
	 *
	 * @param actor receiver that provides audience context, or {@code null} for none
	 * @param message message to serialize
	 * @return serialized component, empty when the message has no text
	 */
	@NotNull
	public static Component serialize(@Nullable Actor actor, @NotNull PlayerMessage message) {
		List<Component> lines = new ArrayList<>();
		for (PlayerMessage entry : message.sequence()) {
			if (entry.getText().isBlank()) continue;

			lines.add(serialize(SerializerContent.builder()
					.receiver(actor)
					.message(entry.getText())
					.placeholders(entry.getPlaceholders())
					.build()));
		}

		return Component.join(JoinConfiguration.newlines(), lines);
	}

	/**
	 * Serializes pre-built serializer content, applying the default Identica scope when absent.
	 *
	 * @param content serializer content to serialize
	 * @return serialized component
	 */
	@NotNull
	public static Component serialize(@NotNull SerializerContent content) {
		if (content.getScope() == null || content.getScope().isBlank()) {
			return getEngine().serialize(SerializerContent.builder()
					.receiver(content.getReceiver())
					.scope(SCOPE)
					.message(content.getMessage())
					.placeholders(content.getPlaceholders())
					.build());
		}

		return getEngine().serialize(content);
	}

	/**
	 * Renders a message through Keystone's template engine using the default Identica serializer scope.
	 *
	 * @param message message to render
	 * @param placeholders placeholder values keyed by placeholder name without delimiters
	 * @return rendered message
	 */
	public static @NotNull String render(@NotNull String message, @NotNull Map<String, String> placeholders) {
		if (placeholders.isEmpty()) return message;

		return getEngine().renderTemplate(SerializerContent.builder()
				.scope(SCOPE)
				.message(message)
				.placeholders(placeholders)
				.build());
	}

	/**
	 * Creates a fluent Keystone message template using the active serializer engine.
	 *
	 * @param template template message to configure
	 * @return fluent message template
	 */
	public static @NotNull MessageTemplate template(@NotNull String template) {
		return getEngine().template(template);
	}

	/**
	 * Returns the active serializer engine.
	 *
	 * @return initialized serializer engine
	 * @throws IllegalStateException when the serializer has not been initialized yet
	 */
	@NotNull
	public static SerializerEngine getEngine() {
		Provider<SerializerEngine> provider = serializerProvider;
		if (provider == null) throw new IllegalStateException("Serializer has not been initialized");

		return provider.get();
	}
}
