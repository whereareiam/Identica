package me.whereareiam.identica.model.message;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Parts a developer gives a message, by its ID, on top of how it is shown otherwise. A part replaces the message's
 * part of the same type, or adds it when the message had none; a removed type takes that part away. When several
 * presentations give a message a part of the same type, the one with the higher priority wins.
 *
 * <pre>{@code
 * identica.getMessageService().present("credential:password", Presentation.builder()
 *         .part(new BossBarPart("<yellow>Type /login <password>"))   // a part type an addon registers
 *         .build());
 * }</pre>
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class Presentation {
	/**
	 * Parts to show, at most one per type.
	 */
	@Singular
	private final @NotNull List<MessagePart> parts;
	/**
	 * Types of parts the message no longer shows, such as {@code chat} for a message only a dialog shows.
	 */
	@Singular("removed")
	private final @NotNull Set<String> removed;
	/**
	 * Priority against other presentations of the same message; higher wins per part type.
	 */
	private final int priority;
}
