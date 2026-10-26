package me.whereareiam.identica.model.message;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A message Identica or a provider tells a player: what a step asks, why it asks again, why a login is refused, how a
 * command went. It is created from its {@link MessageDefinition}, travels with a step's or journey's result, and is
 * shown by {@code MessageService} in the ways its presentation names, chat by default.
 *
 * <p>The text is the configured template, not yet rendered: renderers fill its placeholders for the player they show
 * it to.</p>
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlayerMessage {
	/**
	 * ID of the message's definition, {@code <owner>:<name>}, such as {@code credential:password-invalid}.
	 */
	@Builder.Default
	private @NotNull String id = "";
	/**
	 * The configured text with its placeholders, in Identica's message format.
	 */
	@Builder.Default
	private @NotNull String text = "";
	/**
	 * Values of the text's placeholders, keyed by placeholder name without delimiters.
	 */
	@Builder.Default
	private @NotNull Map<String, String> placeholders = new LinkedHashMap<>();
	/**
	 * What the message asks the player for, or {@code null} for a message that asks nothing.
	 */
	private @Nullable Prompt prompt;
	/**
	 * Messages told together with this one, below it, such as a warning after a wrong password. Each keeps its own ID,
	 * text and placeholders, so an addon can show it its own way.
	 */
	@Builder.Default
	private @NotNull List<PlayerMessage> appended = new ArrayList<>();
	/**
	 * Title shown with the message, in Identica's message format, or {@code null} for none.
	 */
	private @Nullable String title;
	/**
	 * Subtitle shown with the message's title, in Identica's message format, or {@code null} for none.
	 */
	private @Nullable String subtitle;

	/**
	 * Returns this message with another one told below it, such as a warning below a wrong password. The result keeps
	 * this message's ID and prompt; the added message, and whatever was appended to it, keeps its own ID, text and
	 * placeholders.
	 *
	 * @param other the message to add, or {@code null} to add nothing
	 * @return the combined message
	 */
	public @NotNull PlayerMessage append(@Nullable PlayerMessage other) {
		if (other == null || other.isEmpty()) return this;

		List<PlayerMessage> combined = new ArrayList<>(appended);
		combined.addAll(other.sequence());
		return toBuilder().appended(combined).build();
	}

	/**
	 * Returns this message asking for the given prompt instead of its own, such as a broken password rule told while
	 * a step asks for a new password again.
	 *
	 * @param prompt what the message asks for, or {@code null} to ask nothing
	 * @return the message with the prompt
	 */
	public @NotNull PlayerMessage withPrompt(@Nullable Prompt prompt) {
		return toBuilder().prompt(prompt).build();
	}

	/**
	 * Returns this message with a title shown alongside it, such as the title of a completed registration.
	 *
	 * @param title the title, or {@code null} for none
	 * @param subtitle the subtitle, or {@code null} for none
	 * @return the message with the title
	 */
	public @NotNull PlayerMessage withTitle(@Nullable String title, @Nullable String subtitle) {
		return toBuilder().title(title).subtitle(subtitle).build();
	}

	/**
	 * Returns this message followed by the messages appended to it, each on its own, in the order they are told.
	 *
	 * @return this message without its appended ones, then the appended ones
	 */
	public @NotNull List<PlayerMessage> sequence() {
		List<PlayerMessage> sequence = new ArrayList<>();
		sequence.add(toBuilder().appended(new ArrayList<>()).build());
		sequence.addAll(appended);
		return List.copyOf(sequence);
	}

	/**
	 * Tells whether the message, or a message appended to it, has text to show.
	 *
	 * @return {@code true} when there is text
	 */
	public boolean hasText() {
		return !text.isBlank() || appended.stream().anyMatch(PlayerMessage::hasText);
	}

	/**
	 * Tells whether the message has a title or subtitle to show.
	 *
	 * @return {@code true} when there is a title or subtitle
	 */
	public boolean hasTitle() {
		return (title != null && !title.isBlank()) || (subtitle != null && !subtitle.isBlank());
	}

	/**
	 * Tells whether the message has nothing to show: a blank text, such as one an admin cleared, no prompt, no title
	 * and nothing appended.
	 *
	 * @return {@code true} when there is nothing to show
	 */
	public boolean isEmpty() {
		return !hasText() && prompt == null && !hasTitle();
	}
}
