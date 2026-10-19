package me.whereareiam.identica.addon.dialogs.display.packetevents;

import com.github.retrooper.packetevents.protocol.chat.clickevent.ClickEvent;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.ConfirmationDialog;
import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.NoticeDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.Action;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
import com.github.retrooper.packetevents.protocol.dialog.action.StaticAction;
import com.github.retrooper.packetevents.protocol.dialog.body.DialogBody;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessage;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessageDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.dialog.button.CommonButtonData;
import com.github.retrooper.packetevents.protocol.dialog.input.BooleanInputControl;
import com.github.retrooper.packetevents.protocol.dialog.input.Input;
import com.github.retrooper.packetevents.protocol.dialog.input.InputControl;
import com.github.retrooper.packetevents.protocol.dialog.input.NumberRangeInputControl;
import com.github.retrooper.packetevents.protocol.dialog.input.SingleOptionInputControl;
import com.github.retrooper.packetevents.protocol.dialog.input.TextInputControl;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.presentation.DialogClicks;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds PacketEvents' dialog from the addon's: rendered text, its inputs, and a button per action. A command button, a
 * disconnect button and the button that switches to chat send a click to the proxy, with the dialog's inputs; the proxy
 * runs the command as the player, so the client never has to know it. {@link DialogClickListener} reads the clicks.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PacketEventsDialogMapper {
	private static final int BUTTON_WIDTH = 150;
	private static final int BODY_WIDTH = 200;
	private static final int INPUT_WIDTH = 200;

	static com.github.retrooper.packetevents.protocol.dialog.Dialog of(Dialog dialog, DialogText text) {
		CommonDialogData common = new CommonDialogData(
				text.render(dialog.getTitle()),
				null,
				dialog.isCloseWithEscape(),
				false,
				DialogAction.valueOf(dialog.getAfterAction().name()),
				body(dialog, text),
				inputs(dialog, text)
		);

		List<ActionButton> actions = new ArrayList<>();
		for (int index = 0; index < dialog.getActions().size(); index++)
			actions.add(button(dialog.getId(), String.valueOf(index), dialog.getActions().get(index), text));
		ActionButton exit = dialog.getExitAction() == null
				? null
				: button(dialog.getId(), DialogClicks.EXIT, dialog.getExitAction(), text);
		return switch (dialog.getType()) {
			case NOTICE -> new NoticeDialog(common, actions.isEmpty() ? NoticeDialog.DEFAULT_ACTION : actions.getFirst());
			case CONFIRMATION -> new ConfirmationDialog(common, actions.getFirst(), exit);
			case MULTI_ACTION -> new MultiActionDialog(common, actions, exit, 1);
		};
	}

	private static List<DialogBody> body(Dialog dialog, DialogText text) {
		List<DialogBody> body = new ArrayList<>();
		for (String line : dialog.getBody()) {
			Component rendered = text.render(line);
			if (Component.empty().equals(rendered) || rendered.equals(Component.text(""))) continue;

			body.add(new PlainMessageDialogBody(new PlainMessage(rendered, BODY_WIDTH)));
		}
		return body;
	}

	private static List<Input> inputs(Dialog dialog, DialogText text) {
		return dialog.getInputs().stream().map(input -> new Input(input.getKey(), control(input, text))).toList();
	}

	private static InputControl control(DialogInput input, DialogText text) {
		Component label = text.render(input.getLabel());
		return switch (input.getType()) {
			case TEXT -> new TextInputControl(INPUT_WIDTH, label, true, input.getInitial(), input.getMaxLength(), null);
			case BOOLEAN -> new BooleanInputControl(label, input.isChecked(), input.getOnTrue(), input.getOnFalse());
			case SINGLE_OPTION -> new SingleOptionInputControl(INPUT_WIDTH, input.getOptions().stream()
					.map(option -> new SingleOptionInputControl.Entry(option.getId(), text.render(option.getDisplay()), option.isInitial()))
					.toList(), label, true);
			case NUMBER_RANGE -> new NumberRangeInputControl(INPUT_WIDTH, label, "options.generic_value",
					new NumberRangeInputControl.RangeInfo(input.getStart(), input.getEnd(), null, input.getStep()));
		};
	}

	private static ActionButton button(
			String dialog,
			String button,
			me.whereareiam.identica.addon.dialogs.model.DialogAction action,
			DialogText text
	) {
		return new ActionButton(new CommonButtonData(text.render(action.getLabel()), null, BUTTON_WIDTH), action(dialog, button, action));
	}

	private static @Nullable Action action(String dialog, String button, me.whereareiam.identica.addon.dialogs.model.DialogAction action) {
		String value = action.getValue();
		return switch (action.getType()) {
			case COMMAND -> new DynamicCustomAction(new ResourceLocation(DialogClicks.COMMAND), values(DialogClicks.button(dialog, button)));
			case DISCONNECT -> new DynamicCustomAction(new ResourceLocation(DialogClicks.DISCONNECT), values(DialogClicks.button(dialog, button)));
			case URL -> new StaticAction(ClickEvent.fromAdventure(net.kyori.adventure.text.event.ClickEvent.openUrl(value)));
			case COPY -> new StaticAction(ClickEvent.fromAdventure(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(value)));
			case CLOSE -> null;
			case CHAT -> new DynamicCustomAction(new ResourceLocation(DialogClicks.CHAT), new NBTCompound());
		};
	}

	private static NBTCompound values(Map<String, String> values) {
		NBTCompound compound = new NBTCompound();
		values.forEach((key, value) -> compound.setTag(key, new NBTString(value)));

		return compound;
	}
}
