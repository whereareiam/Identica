package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.Prompt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The messages of the provider choice, where a player picks how a scenario gets done. Their texts sit in core's
 * {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProviderChoiceMessages {
	/**
	 * What the provider choice asks for: the option the player picks.
	 */
	public static final Prompt PROMPT = Prompt.builder()
			.id("identica:provider-choice")
			.field(Prompt.Field.of("option", Prompt.FieldType.CHOICE))
			.command("enroll")
			.build();

	/**
	 * The provider choice: the options listed with {@code engine.journey.step.enrollment}. Its {@code entries}
	 * placeholder holds the rendered option lines, and its prompt's choice field lists the options.
	 */
	public static final MessageDefinition<Messages> CHOICE = MessageDefinition.<Messages>builder()
			.id("identica:provider-choice")
			.text(messages -> MessageDefinition.lines(messages.getEngine().getJourney().getStep().getEnrollment().getBody()))
			.placeholder("entries")
			.prompt(PROMPT)
			.build();

	/**
	 * No option is left for the player to choose from.
	 */
	public static final MessageDefinition<Messages> EMPTY =
			CoreDefinitions.lines("identica:provider-choice-empty", messages -> messages.getEngine().getJourney().getStep().getEnrollment().getEmpty());

	/**
	 * The player picked an option while nothing waited for a choice.
	 */
	public static final MessageDefinition<Messages> NO_PENDING =
			CoreDefinitions.text("identica:provider-choice-no-pending", messages -> messages.getCommands().getEnroll().getNoPending());

	/**
	 * The player's pick finished their journey.
	 */
	public static final MessageDefinition<Messages> COMPLETED =
			CoreDefinitions.text("identica:provider-choice-completed", messages -> messages.getCommands().getEnroll().getCompleted());

	/**
	 * Returns every message of this topic.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<?>> all() {
		return List.of(
				CHOICE,
				EMPTY,
				NO_PENDING,
				COMPLETED
		);
	}
}
