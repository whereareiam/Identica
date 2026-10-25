package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages about a player's account. Their texts sit in core's {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccountMessages {
	/**
	 * A registration found an account that already exists.
	 */
	public static final MessageDefinition<Messages> ALREADY_EXISTS =
			CoreDefinitions.lines("identica:registration/account-already-exists", messages -> messages.getScenarios().getRegistration().getAccountAlreadyExists());

	/**
	 * An administrator cleared the player's account.
	 */
	public static final MessageDefinition<Messages> CLEARED =
			CoreDefinitions.lines("identica:account-cleared", messages -> messages.getCommands().getAdmin().getClear().getDisconnect());

	/**
	 * An administrator deleted the player's account.
	 */
	public static final MessageDefinition<Messages> DELETED =
			CoreDefinitions.lines("identica:account-deleted", messages -> messages.getCommands().getAdmin().getDelete().getDisconnect());

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				ALREADY_EXISTS,
				CLEARED,
				DELETED
		);
	}
}
