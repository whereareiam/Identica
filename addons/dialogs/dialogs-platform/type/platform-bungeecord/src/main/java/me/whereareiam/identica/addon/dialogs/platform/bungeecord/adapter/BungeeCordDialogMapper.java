package me.whereareiam.identica.addon.dialogs.platform.bungeecord.adapter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.platform.bungeecord.listener.ChatSwitchListener;
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
import net.md_5.bungee.api.dialog.action.RunCommandAction;
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
import java.util.Optional;

/**
 * Builds BungeeCord's dialog from the addon's: rendered text, its inputs, and a button per action. A command is a
 * template the client fills from the inputs, which a command without {@code $(key)} is too; the button that switches
 * to chat sends the custom click {@link ChatSwitchListener} reads.
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

		List<ActionButton> actions = dialog.getActions().stream()
				.map(action -> button(action, text))
				.flatMap(Optional::stream)
				.toList();
		ActionButton exit = dialog.getExitAction() == null ? null : button(dialog.getExitAction(), text).orElse(null);
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

	/**
	 * Builds a button, or none for a command that is unavailable, such as one an administrator disabled.
	 */
	private static Optional<ActionButton> button(DialogAction action, DialogText text) {
		BaseComponent label = bungee(text.render(action.getLabel()));
		String value = action.getValue();
		return switch (action.getType()) {
			case COMMAND, RUN -> Optional.ofNullable(text.command(action))
					.map(command -> new ActionButton(label, null, BUTTON_WIDTH, new RunCommandAction(command)));
			case URL -> Optional.of(new ActionButton(label, null, BUTTON_WIDTH, new StaticAction(new ClickEvent(ClickEvent.Action.OPEN_URL, value))));
			case COPY -> Optional.of(new ActionButton(label, null, BUTTON_WIDTH,
					new StaticAction(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))));
			case CLOSE -> Optional.of(new ActionButton(label, null, BUTTON_WIDTH, null));
			case CHAT -> Optional.of(new ActionButton(label, null, BUTTON_WIDTH, new CustomClickAction(ChatSwitchListener.CHAT_CLICK)));
		};
	}

	private static BaseComponent bungee(Component component) {
		return new TextComponent(BungeeComponentSerializer.get().serialize(component));
	}
}
