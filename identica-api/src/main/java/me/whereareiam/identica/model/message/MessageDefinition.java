package me.whereareiam.identica.model.message;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A message Identica or a provider can tell a player, defined once by its owner: a stable ID, where its text sits in
 * the owner's messages document, the placeholders the text may use and, for a message that asks the player for
 * something, its prompt. The owner registers its definitions with {@code MessageService}, so every message is known,
 * and can be presented in other ways than chat, before it is first sent.
 *
 * <p>The text stays in the owner's messages document, which admins edit; a message is created from the document's
 * current state, so a reload applies at once. The ID is the contract others rely on: keep it stable once released,
 * even when the text moves within the document.</p>
 *
 * <pre>{@code
 * public static final MessageDefinition<CredentialMessages> PASSWORD_INVALID =
 *         MessageDefinition.<CredentialMessages>builder()
 *                 .id("credential:password-invalid")
 *                 .text(messages -> messages.getScenario().getAuthentication().getStatus().getInvalid())
 *                 .prompt(PASSWORD)
 *                 .build();
 *
 * return StepResult.waiting(PASSWORD_INVALID.create(messages.get()));
 * }</pre>
 *
 * @param <D> the owner's messages document
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class MessageDefinition<D> {
	/**
	 * ID of the message, {@code <owner>:<name>}, in lower case.
	 */
	@ToString.Include
	private final @NotNull String id;
	/**
	 * Reads the configured text from the owner's messages document.
	 */
	private final @NotNull Function<D, String> text;
	/**
	 * Placeholders the text may use, by name without delimiters.
	 */
	@Singular
	private final @NotNull Set<String> placeholders;
	/**
	 * What the message asks the player for, or {@code null} for a message that asks nothing.
	 */
	private final @Nullable Prompt prompt;

	/**
	 * Creates a message with the document's current text and no placeholder values.
	 *
	 * @param document the owner's messages document
	 * @return the message
	 */
	public @NotNull PlayerMessage create(@NotNull D document) {
		return create(document, Map.of());
	}

	/**
	 * Creates a message with the document's current text.
	 *
	 * @param document the owner's messages document
	 * @param placeholders values of the text's placeholders, keyed by name without delimiters
	 * @return the message
	 */
	public @NotNull PlayerMessage create(@NotNull D document, @NotNull Map<String, String> placeholders) {
		String current = text.apply(document);

		return PlayerMessage.builder()
				.id(id.toLowerCase(Locale.ROOT))
				.text(current != null ? current : "")
				.placeholders(new LinkedHashMap<>(placeholders))
				.prompt(prompt)
				.build();
	}

	/**
	 * Builds the text of a message whose document keeps it as lines.
	 *
	 * @param lines configured lines, possibly {@code null}
	 * @return the lines joined by line breaks, or an empty text
	 */
	public static @NotNull String lines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty()) return "";

		return String.join("\n", lines);
	}
}
