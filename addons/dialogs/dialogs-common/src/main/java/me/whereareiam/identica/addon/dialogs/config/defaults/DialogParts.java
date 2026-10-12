package me.whereareiam.identica.addon.dialogs.config.defaults;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.addon.dialogs.type.InputType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the shipped dialogs share: a multi-action form without a title, with a white line that greets the player, a
 * gray line that says what to do, the {@code {answer}} line that says in the dialog's own words why Identica asks
 * again, one text input named like the field of the prompt it answers, one button that answers it, and a button below
 * them that leaves the server. They have no
 * button that switches to chat; an administrator adds one to a dialog's file.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DialogParts {
	static final String ANSWER = "{answer}";
	static final String LEAVE = "<white>You left the server.";

	static DialogDocument form(
			DialogDocument dialog,
			String greeting,
			String instruction,
			DialogDocument.Input input,
			DialogDocument.Action submit,
			Map<String, String> answers
	) {
		dialog.setType(DialogType.MULTI_ACTION);
		dialog.setTitle("");
		dialog.setBody(List.of("<white>" + greeting, "<gray>" + instruction, ANSWER));
		dialog.setAnswers(answers);
		dialog.setInputs(List.of(input));
		dialog.setActions(List.of(submit));
		dialog.setExitAction(action("<white>Leave", ActionType.DISCONNECT, LEAVE));
		dialog.setCloseWithEscape(false);
		dialog.setAfterAction(AfterAction.NONE);
		return dialog;
	}

	static DialogDocument.Input text(String key, String label, int maxLength) {
		DialogDocument.Input input = new DialogDocument.Input();
		input.setKey(key);
		input.setType(InputType.TEXT);
		input.setLabel("<white>" + label);
		input.setMaxLength(maxLength);
		return input;
	}

	/**
	 * Builds a dialog's answers in the order given, so the file lists them that way.
	 *
	 * @param answers pairs of an answer's ID and its text
	 */
	static Map<String, String> answers(String... answers) {
		Map<String, String> ordered = new LinkedHashMap<>();
		for (int index = 0; index + 1 < answers.length; index += 2)
			ordered.put(answers[index], answers[index + 1]);

		return ordered;
	}

	static String problem(String text) {
		return "<red>" + text;
	}

	/**
	 * Builds the button that answers the prompt of the message the dialog shows.
	 *
	 * @param label the button's text, shown green
	 */
	static DialogDocument.Action submit(String label) {
		return action("<green>" + label, ActionType.ANSWER, "");
	}

	private static DialogDocument.Action action(String label, ActionType type, String value) {
		DialogDocument.Action action = new DialogDocument.Action();
		action.setLabel(label);
		action.setType(type);
		action.setValue(value);
		return action;
	}
}
