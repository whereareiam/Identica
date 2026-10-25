package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages about a connection Identica cannot take or route. Their texts sit in core's
 * {@code messages.yml}; a scenario's own missing-server message is in {@link ScenarioMessages}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConnectionMessages {
	/**
	 * A handshake policy refused the connection without a reason of its own.
	 */
	public static final MessageDefinition<Messages> HANDSHAKE_DENIED =
			CoreDefinitions.lines("identica:handshake-denied", messages -> messages.getEngine().getPrepare().getHandshakeDenied());

	/**
	 * The server routing picked does not exist.
	 */
	public static final MessageDefinition<Messages> MISSING_SERVER =
			CoreDefinitions.lines("identica:missing-server", messages -> messages.getRouting().getMissingServer());

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				HANDSHAKE_DENIED,
				MISSING_SERVER
		);
	}
}
