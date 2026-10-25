package me.whereareiam.identica.model.message;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import me.whereareiam.identica.message.PartRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A kind of part messages can be shown with: its ID, its class, the renderer that shows it and what to show instead
 * when the renderer cannot. Identica registers {@code chat}; an addon registers its own kinds, such as a dialog.
 *
 * <pre>{@code
 * identica.getMessageService().registerPartType(PartType.<DialogPart>builder()
 *         .id("dialog")
 *         .partClass(DialogPart.class)
 *         .renderer(new DialogPartRenderer())
 *         .fallback(ChatPart.TYPE)
 *         .build());
 * }</pre>
 *
 * @param <P> part class
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@Builder
public class PartType<P extends MessagePart> {
	/**
	 * ID of the kind, the {@link MessagePart#type()} of its parts.
	 */
	@ToString.Include
	private final @NotNull String id;
	/**
	 * Class of the kind's parts.
	 */
	private final @NotNull Class<P> partClass;
	/**
	 * Shows the kind's parts.
	 */
	private final @NotNull PartRenderer<P> renderer;
	/**
	 * Types shown instead, in order, when the renderer cannot show a part to the player.
	 */
	@Singular
	private final @NotNull List<String> fallbacks;
	/**
	 * Builds the part a message gets when this type is shown as another type's fallback without the message naming it,
	 * or {@code null} when the type cannot be a fallback on its own.
	 */
	private final @Nullable Function<PlayerMessage, P> defaultPart;
}
