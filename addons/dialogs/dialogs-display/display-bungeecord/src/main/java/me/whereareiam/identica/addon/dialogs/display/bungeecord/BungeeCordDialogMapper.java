package me.whereareiam.identica.addon.dialogs.display.bungeecord;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.presentation.DialogClicks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.dialog.ConfirmationDialog;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.NoticeDialog;
import net.md_5.bungee.api.dialog.action.Action;
import net.md_5.bungee.api.dialog.action.ActionButton;
import net.md_5.bungee.api.dialog.action.CustomClickAction;
import net.md_5.bungee.api.dialog.action.StaticAction;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;
import net.md_5.bungee.api.dialog.input.BooleanInput;
import net.md_5.bungee.api.dialog.input.InputOption;
import net.md_5.bungee.api.dialog.input.NumberRangeInput;
import net.md_5.bungee.api.dialog.input.SingleOptionInput;
import net.md_5.bungee.api.dialog.input.TextInput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds BungeeCord's dialog from the addon's: rendered text, its inputs, and a button per action. A command button, a
 * disconnect button and the button that switches to chat send a click to the proxy, with the dialog's inputs; the proxy
 * runs the command as the player, so the client never has to know it. {@link DialogClickListener} reads the clicks.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BungeeCordDialogMapper {
	private static final int BUTTON_WIDTH = 150;
	private static final int BODY_WIDTH = 200;
	private static final int INPUT_WIDTH = 200;

	static net.md_5.bungee.api.dialog.Dialog of(Dialog dialog, DialogText text) {
		DialogBase base = new DialogBase(bungee(text.render(dialog.getTitle())))
				.body(body(dialog, text))
				.inputs(dialog.getInputs().stream().map(input -> input(input, text)).toList())
				.canCloseWithEscape(dialog.isCloseWithEscape())
				.pause(false)
				.afterAction(DialogBase.AfterAction.valueOf(dialog.getAfterAction().name()));

		List<ActionButton> actions = new ArrayList<>();
		for (int index = 0; index < dialog.getActions().size(); index++)
			actions.add(button(dialog.getId(), String.valueOf(index), dialog.getActions().get(index), text));
		ActionButton exit = dialog.getExitAction() == null
				? null
				: button(dialog.getId(), DialogClicks.EXIT, dialog.getExitAction(), text);
		return switch (dialog.getType()) {
			case NOTICE -> actions.isEmpty() ? new NoticeDialog(base) : new NoticeDialog(base, actions.getFirst());
			case CONFIRMATION -> new ConfirmationDialog(base, actions.getFirst(), exit);
			case MULTI_ACTION -> new MultiActionDialog(base, actions, 1, exit);
		};
	}

	private static List<DialogBody> body(Dialog dialog, DialogText text) {
		List<DialogBody> body = new ArrayList<>();
		for (String line : dialog.getBody()) {
			Component rendered = text.render(line);
			if (Component.empty().equals(rendered) || rendered.equals(Component.text(""))) continue;

			body.add(new PlainMessageBody(bungee(rendered), BODY_WIDTH));
		}
		return body;
	}

	private static net.md_5.bungee.api.dialog.input.DialogInput input(DialogInput input, DialogText text) {
		BaseComponent label = bungee(text.render(input.getLabel()));
		return switch (input.getType()) {
			case TEXT -> new TextInput(input.getKey(), INPUT_WIDTH, label, true, input.getInitial(), input.getMaxLength());
			case BOOLEAN -> new BooleanInput(input.getKey(), label, input.isChecked(), input.getOnTrue(), input.getOnFalse());
			case SINGLE_OPTION -> new SingleOptionInput(input.getKey(), INPUT_WIDTH, label, true, input.getOptions().stream()
					.map(option -> new InputOption(option.getId(), bungee(text.render(option.getDisplay())), option.isInitial()))
					.toList());
			case NUMBER_RANGE -> new NumberRangeInput(input.getKey(), INPUT_WIDTH, label, "options.generic_value",
					input.getStart(), input.getEnd(), null, input.getStep());
		};
	}

	private static ActionButton button(String dialog, String button, DialogAction action, DialogText text) {
		return new ActionButton(bungee(text.render(action.getLabel())), null, BUTTON_WIDTH, action(dialog, button, action));
	}

	private static @Nullable Action action(String dialog, String button, DialogAction action) {
		String value = action.getValue();
		return switch (action.getType()) {
			case COMMAND -> new CustomClickAction(DialogClicks.COMMAND).additions(values(DialogClicks.button(dialog, button)));
			case DISCONNECT -> new CustomClickAction(DialogClicks.DISCONNECT).additions(values(DialogClicks.button(dialog, button)));
			case URL -> new StaticAction(new ClickEvent(ClickEvent.Action.OPEN_URL, value));
			case COPY -> new StaticAction(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value));
			case CLOSE -> null;
			case CHAT -> new CustomClickAction(DialogClicks.CHAT);
		};
	}

	private static JsonObject values(Map<String, String> values) {
		JsonObject object = new JsonObject();
		values.forEach(object::addProperty);

		return object;
	}

	private static BaseComponent bungee(Component component) {
		return new TextComponent(BungeeComponentSerializer.get().serialize(component));
	}
}
