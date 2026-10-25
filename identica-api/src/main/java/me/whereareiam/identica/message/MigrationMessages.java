package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages about moving an account to another provider. Their texts sit in core's {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MigrationMessages {
	/**
	 * A pending migration was cancelled.
	 */
	public static final MessageDefinition<Messages> CANCELLED =
			CoreDefinitions.lines("identica:migration/cancelled", messages -> messages.getScenarios().getMigration().getCancelled());

	/**
	 * A migration cannot start, because another account holds the username.
	 */
	public static final MessageDefinition<Messages> LOCKED =
			CoreDefinitions.text("identica:migration/locked", messages -> messages.getCommands().getMigration().getLocked());

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				CANCELLED,
				LOCKED
		);
	}
}
