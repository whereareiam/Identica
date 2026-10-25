package me.whereareiam.identica.model.message;

import lombok.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * What a message asks the player for, described well enough for another way of asking than chat, such as a dialog:
 * the fields the player fills and the command that answers them. Every message that asks for the same thing, such as
 * a password asked for the first time and again after a wrong one, carries the same prompt.
 *
 * <pre>{@code
 * Prompt password = Prompt.builder()
 *         .id("credential:password")
 *         .field(Prompt.Field.of("password", Prompt.FieldType.SECRET))
 *         .command("login")
 *         .build();
 * }</pre>
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Prompt {
	/**
	 * ID of what is asked, {@code <owner>:<name>}, such as {@code credential:password}.
	 */
	@Builder.Default
	private @NotNull String id = "";
	/**
	 * The fields the player fills, in the order the answering command takes them as arguments.
	 */
	@Singular
	private @NotNull List<Field> fields;
	/**
	 * Key of the command definition that answers the prompt, such as {@code login}; the command line is built from its
	 * configured alias, see {@code MessageService#answer}.
	 */
	@Builder.Default
	private @NotNull String command = "";

	/**
	 * One value the player gives.
	 */
	@Getter
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Field {
		/**
		 * Name of the field, also the name of the command argument it fills.
		 */
		private @NotNull String name = "";
		/**
		 * What kind of value the field takes.
		 */
		private @NotNull FieldType type = FieldType.TEXT;
		/**
		 * Values the player picks from, for a {@link FieldType#CHOICE}; empty otherwise.
		 */
		private @NotNull List<Option> options = new ArrayList<>();

		/**
		 * Creates a field without options.
		 *
		 * @param name field name
		 * @param type kind of value
		 * @return the field
		 */
		public static @NotNull Field of(@NotNull String name, @NotNull FieldType type) {
			return new Field(name, type, List.of());
		}

		/**
		 * Creates a field the player fills by picking one of the options.
		 *
		 * @param name field name
		 * @param options values to pick from
		 * @return the field
		 */
		public static @NotNull Field choice(@NotNull String name, @NotNull List<Option> options) {
			return new Field(name, FieldType.CHOICE, List.copyOf(options));
		}
	}

	/**
	 * A value of a {@link FieldType#CHOICE} field.
	 */
	@Getter
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Option {
		/**
		 * The value the command receives, such as {@code credential} or {@code website:guided}.
		 */
		private @NotNull String value = "";
		/**
		 * Text naming the value for the player, in Identica's message format.
		 */
		private @NotNull String label = "";
	}

	/**
	 * Kinds of values a field takes.
	 */
	public enum FieldType {
		/**
		 * Free text, shown as typed.
		 */
		TEXT,
		/**
		 * A secret, such as a password, hidden while typed.
		 */
		SECRET,
		/**
		 * A short code, such as one from an authenticator app.
		 */
		CODE,
		/**
		 * One of the field's options.
		 */
		CHOICE
	}
}
