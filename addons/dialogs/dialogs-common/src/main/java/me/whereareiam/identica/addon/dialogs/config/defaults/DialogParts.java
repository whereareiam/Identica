package me.whereareiam.identica.addon.dialogs.config.defaults;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.addon.dialogs.type.InputType;

import java.util.List;

/**
 * What the shipped dialogs share: a multi-action form with one text input and one button that runs a command, a
 * button below it that switches to chat, and the {@code {message}} line that shows Identica's answer to the
 * previous attempt.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DialogParts {
	static final String TITLE = "<green><bold>Identica";
	static final String MESSAGE = "{message}";

	static DialogDocument form(DialogDocument dialog, List<String> body, DialogDocument.Input input, DialogDocument.Action submit) {
		dialog.setType(DialogType.MULTI_ACTION);
		dialog.setTitle(TITLE);
		dialog.setBody(body);
		dialog.setInputs(List.of(input));
		dialog.setActions(List.of(submit));
		dialog.setExitAction(chat("<gray>Use chat instead"));
		dialog.setCloseWithEscape(false);
		dialog.setAfterAction(AfterAction.NONE);
		return dialog;
	}

	static DialogDocument.Input text(String key, String label, int maxLength) {
		DialogDocument.Input input = new DialogDocument.Input();
		input.setKey(key);
		input.setType(InputType.TEXT);
		input.setLabel(label);
		input.setMaxLength(maxLength);
		return input;
	}

	/**
	 * A button that runs an Identica command, named by its definition, with the given arguments.
	 */
	static DialogDocument.Action command(String label, String definition, String... arguments) {
		DialogDocument.Action action = new DialogDocument.Action();
		action.setLabel(label);
		action.setType(ActionType.COMMAND);
		action.setCommand(definition);
		action.setArguments(List.of(arguments));
		return action;
	}

	private static DialogDocument.Action chat(String label) {
		DialogDocument.Action action = new DialogDocument.Action();
		action.setLabel(label);
		action.setType(ActionType.CHAT);
		return action;
	}
}
