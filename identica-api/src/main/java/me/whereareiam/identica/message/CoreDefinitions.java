package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * Builds core's definitions whose text is one entry of {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CoreDefinitions {
	static @NotNull MessageDefinition<Messages> lines(@NotNull String id, @NotNull Function<Messages, List<String>> lines) {
		return MessageDefinition.<Messages>builder()
				.id(id)
				.text(messages -> MessageDefinition.lines(lines.apply(messages)))
				.build();
	}

	static @NotNull MessageDefinition<Messages> text(@NotNull String id, @NotNull Function<Messages, String> text) {
		return MessageDefinition.<Messages>builder()
				.id(id)
				.text(text)
				.build();
	}
}
