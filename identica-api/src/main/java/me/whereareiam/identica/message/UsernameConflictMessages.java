package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages of a username two players want. Their texts sit in core's {@code messages.yml} and in
 * {@code username/messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UsernameConflictMessages {
	/**
	 * Another connection is logging in under the username.
	 */
	public static final MessageDefinition<Messages> HELD =
			CoreDefinitions.lines("identica:username-held", messages -> messages.getEngine().getUsernameHeld());

	/**
	 * Another account holds the username the player wants; placeholder {@code username}.
	 */
	public static final MessageDefinition<UsernameMessages> TAKEN = MessageDefinition.<UsernameMessages>builder()
			.id("identica:username-taken")
			.text(messages -> MessageDefinition.lines(messages.getPipeline().getPolicy().getTaken()))
			.placeholder("username")
			.build();

	/**
	 * The player's account lost its username to its rightful holder; placeholders {@code username}, the name it had,
	 * and {@code renamed}, the name it has now.
	 */
	public static final MessageDefinition<UsernameMessages> HOLDER_REPLACED = MessageDefinition.<UsernameMessages>builder()
			.id("identica:username-holder-replaced")
			.text(messages -> MessageDefinition.lines(messages.getPipeline().getPolicy().getHolderReplaced()))
			.placeholder("username")
			.placeholder("renamed")
			.build();

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				HELD,
				TAKEN,
				HOLDER_REPLACED
		);
	}
}
