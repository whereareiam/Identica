package me.whereareiam.identica.message;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.message.MessagePart;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the parts of one {@link me.whereareiam.identica.model.message.PartType} to a player.
 *
 * <p>A part that stays visible, such as a dialog, returns {@link PartRendering#shown(PartRendering.Close)}: Identica
 * takes it down once the message is no longer current, because the player answered, the next message to them replaced
 * it, or their journey ended.</p>
 *
 * @param <P> part class
 */
@FunctionalInterface
public interface PartRenderer<P extends MessagePart> {
	/**
	 * Shows a part of a message to a player.
	 *
	 * @param part the part
	 * @param message the message it belongs to, with its text, placeholders and prompt
	 * @param recipient the player
	 * @return what the renderer did
	 */
	@NotNull PartRendering render(@NotNull P part, @NotNull PlayerMessage message, @NotNull Identity recipient);
}
