package me.whereareiam.identica.addon.dialogs.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.model.DialogOption;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.addon.dialogs.type.InputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * One file in {@code dialogs/}: a dialog, named after its file. A field the file leaves out takes the value
 * {@link #toDialog(String)} names, so Configura can fill in the shipped dialogs' defaults, which it does only for
 * fields that are not set.
 */
@Getter
@Setter
@ToString
public class DialogDocument {
	private @Nullable DialogType type;
	private @Nullable String title;
	private @Nullable List<String> body;
	private @Nullable List<Input> inputs;
	private @Nullable List<Action> actions;
	private @Nullable Action exitAction;
	private @Nullable Boolean closeWithEscape;
	private @Nullable AfterAction afterAction;

	/**
	 * Returns the dialog as the addon uses it: a multi-action dialog without a title, body, inputs or buttons that
	 * Escape cannot close and that stays open after a button, unless the file says otherwise.
	 *
	 * @param id the dialog's ID, the file's name without the extension
	 * @return the dialog
	 */
	public @NotNull Dialog toDialog(@NotNull String id) {
		Dialog.DialogBuilder dialog = Dialog.builder()
				.id(id)
				.type(Objects.requireNonNullElse(type, DialogType.MULTI_ACTION))
				.title(Objects.requireNonNullElse(title, ""))
				.body(Objects.requireNonNullElse(body, List.of()))
				.exitAction(exitAction == null ? null : exitAction.toAction())
				.closeWithEscape(Boolean.TRUE.equals(closeWithEscape))
				.afterAction(Objects.requireNonNullElse(afterAction, AfterAction.NONE));
		Objects.requireNonNullElse(inputs, List.<Input>of()).forEach(input -> dialog.input(input.toInput()));
		Objects.requireNonNullElse(actions, List.<Action>of()).forEach(action -> dialog.action(action.toAction()));
		return dialog.build();
	}

	/**
	 * An input; fields that do not belong to its type are ignored.
	 */
	@Getter
	@Setter
	@ToString
	public static class Input {
		private @Nullable String key;
		private @Nullable InputType type;
		private @Nullable String label;
		private @Nullable String initial;
		private @Nullable Integer maxLength;
		private @Nullable Boolean checked;
		private @Nullable String onTrue;
		private @Nullable String onFalse;
		private @Nullable List<Option> options;
		private @Nullable Float start;
		private @Nullable Float end;
		private @Nullable Float step;

		DialogInput toInput() {
			DialogInput.DialogInputBuilder input = DialogInput.builder()
					.key(Objects.requireNonNullElse(key, ""))
					.type(Objects.requireNonNullElse(type, InputType.TEXT))
					.label(Objects.requireNonNullElse(label, ""))
					.initial(Objects.requireNonNullElse(initial, ""))
					.maxLength(Objects.requireNonNullElse(maxLength, 32))
					.checked(Boolean.TRUE.equals(checked))
					.onTrue(Objects.requireNonNullElse(onTrue, "true"))
					.onFalse(Objects.requireNonNullElse(onFalse, "false"))
					.start(Objects.requireNonNullElse(start, 0F))
					.end(Objects.requireNonNullElse(end, 0F))
					.step(step);
			Objects.requireNonNullElse(options, List.<Option>of()).forEach(option -> input.option(option.toOption()));
			return input.build();
		}
	}

	/**
	 * An option of a single-option input.
	 */
	@Getter
	@Setter
	@ToString
	public static class Option {
		private @Nullable String id;
		private @Nullable String display;
		private @Nullable Boolean initial;

		DialogOption toOption() {
			String optionId = Objects.requireNonNullElse(id, "");
			return new DialogOption(optionId, Objects.requireNonNullElse(display, optionId), Boolean.TRUE.equals(initial));
		}
	}

	/**
	 * A button.
	 */
	@Getter
	@Setter
	@ToString
	public static class Action {
		private @Nullable String label;
		private @Nullable ActionType type;
		/**
		 * The definition ID of the Identica command a {@link ActionType#COMMAND} button runs, such as {@code login}.
		 */
		private @Nullable String command;
		/**
		 * The arguments of the Identica command, each {@code $(key)} replaced by the value of that input.
		 */
		private @Nullable List<String> arguments;
		/**
		 * The command line of a {@link ActionType#RUN} button, the URL to open or the text to copy.
		 */
		private @Nullable String value;

		DialogAction toAction() {
			ActionType actionType = Objects.requireNonNullElse(type, ActionType.CLOSE);
			if (actionType == ActionType.COMMAND)
				return new DialogAction(Objects.requireNonNullElse(label, ""), actionType, Objects.requireNonNullElse(command, ""),
						List.copyOf(Objects.requireNonNullElse(arguments, List.of())));

			return new DialogAction(Objects.requireNonNullElse(label, ""), actionType, Objects.requireNonNullElse(value, ""), List.of());
		}
	}
}
