package me.whereareiam.identica.model.message;

import org.jetbrains.annotations.NotNull;

/**
 * One way a message is shown, such as a chat line or a dialog. Identica shows messages in chat; addons add other kinds
 * of parts with a {@link PartType}, and anyone can give a message more parts, or other ones, with a
 * {@link Presentation}.
 */
public interface MessagePart {
	/**
	 * Returns the ID of the part's kind, the ID of its {@link PartType}, such as {@code chat}.
	 *
	 * @return part type ID
	 */
	@NotNull String type();
}
