package me.whereareiam.identica.example.provider;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.example.provider.config.ExampleMessages;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.Prompt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The provider's messages to players. Each has an ID addons rely on, such as one that shows the passphrase prompt as
 * a dialog, and reads its text from {@code messages.yml}, so admins keep editing it there.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExampleMessageDefinitions {
	/**
	 * What the step asks for: the passphrase, answered with {@code /passphrase <passphrase>}.
	 */
	public static final Prompt PASSPHRASE_PROMPT = Prompt.builder()
			.id("example:passphrase")
			.field(Prompt.Field.of("passphrase", Prompt.FieldType.SECRET))
			.command(ExampleProvider.PASSPHRASE_COMMAND)
			.build();

	/**
	 * Asks for the passphrase.
	 */
	public static final MessageDefinition<ExampleMessages> PASSPHRASE = MessageDefinition.<ExampleMessages>builder()
			.id("example:passphrase")
			.text(ExampleMessages::getPrompt)
			.prompt(PASSPHRASE_PROMPT)
			.build();

	/**
	 * The passphrase was wrong; asks again.
	 */
	public static final MessageDefinition<ExampleMessages> PASSPHRASE_INVALID = MessageDefinition.<ExampleMessages>builder()
			.id("example:passphrase-invalid")
			.text(ExampleMessages::getInvalid)
			.prompt(PASSPHRASE_PROMPT)
			.build();

	/**
	 * The player typed a passphrase while nothing waited for one.
	 */
	public static final MessageDefinition<ExampleMessages> NO_PENDING = MessageDefinition.<ExampleMessages>builder()
			.id("example:no-pending")
			.text(ExampleMessages::getNoPending)
			.build();

	/**
	 * Returns every message of the provider, which it registers when it is enabled.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<ExampleMessages>> all() {
		return List.of(PASSPHRASE, PASSPHRASE_INVALID, NO_PENDING);
	}
}
