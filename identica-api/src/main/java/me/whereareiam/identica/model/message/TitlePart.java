package me.whereareiam.identica.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a message's title and subtitle on the player's screen. A message with a title, such as one that tells a
 * player their registration completed, shows this part unless a {@link Presentation} removes it.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TitlePart implements MessagePart {
	/**
	 * ID of the title part type.
	 */
	public static final String TYPE = "title";

	/**
	 * Title shown instead of the message's own, in Identica's message format, or {@code null} for the message's title.
	 */
	private @Nullable String title;
	/**
	 * Subtitle shown instead of the message's own, or {@code null} for the message's subtitle.
	 */
	private @Nullable String subtitle;

	/**
	 * Returns the part that shows the message's own title and subtitle.
	 *
	 * @return the part
	 */
	public static @NotNull TitlePart ofMessage() {
		return new TitlePart(null, null);
	}

	@Override
	public @NotNull String type() {
		return TYPE;
	}
}
