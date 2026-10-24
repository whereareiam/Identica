package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages of a journey that cannot go on. Their texts sit in core's {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JourneyMessages {
	/**
	 * A journey stage that has to complete did not.
	 */
	public static final MessageDefinition<Messages> STAGE_NO_COMPLETION =
			CoreDefinitions.lines("identica:stage-no-completion", messages -> messages.getEngine().getJourney().getStage().getNoCompletion());

	/**
	 * A journey step returned no result.
	 */
	public static final MessageDefinition<Messages> STEP_NO_STATUS =
			CoreDefinitions.lines("identica:step-no-status", messages -> messages.getEngine().getJourney().getStep().getNoStatus());

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				STAGE_NO_COMPLETION,
				STEP_NO_STATUS
		);
	}
}
