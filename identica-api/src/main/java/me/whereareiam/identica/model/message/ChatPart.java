package me.whereareiam.identica.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a message as chat lines, which is how Identica shows every message unless a {@link Presentation} says
 * otherwise.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ChatPart implements MessagePart {
	/**
	 * ID of the chat part type.
	 */
	public static final String TYPE = "chat";

	/**
	 * Text shown instead of the message's own, in Identica's message format, or {@code null} for the message's text.
	 */
	private @Nullable String text;

	/**
	 * Returns the part that shows the message's own text.
	 *
	 * @return the part
	 */
	public static @NotNull ChatPart ofMessage() {
		return new ChatPart(null);
	}

	@Override
	public @NotNull String type() {
		return TYPE;
	}
}
