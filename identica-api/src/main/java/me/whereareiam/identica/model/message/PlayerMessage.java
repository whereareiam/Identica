package me.whereareiam.identica.model.message;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
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
	 * Returns this message with another one's text added as further lines, such as a warning below a wrong password.
	 * The result keeps this message's ID and prompt; the placeholders of both apply.
	 *
	 * @param other the message to add, or {@code null} to add nothing
	 * @return the combined message
	 */
	public @NotNull PlayerMessage append(@Nullable PlayerMessage other) {
		if (other == null || other.getText().isBlank()) return this;
		if (text.isBlank()) return toBuilder().text(other.getText()).placeholders(merged(other)).build();

		return toBuilder()
				.text(text + "\n" + other.getText())
				.placeholders(merged(other))
				.build();
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

	private @NotNull Map<String, String> merged(@NotNull PlayerMessage other) {
		Map<String, String> merged = new LinkedHashMap<>(other.getPlaceholders());
		merged.putAll(placeholders);

		return merged;
	}

	/**
	 * Tells whether the message has nothing to show: a blank text, such as one an admin cleared, and no prompt.
	 *
	 * @return {@code true} when there is nothing to show
	 */
	public boolean isEmpty() {
		return text.isBlank() && prompt == null;
	}
}
