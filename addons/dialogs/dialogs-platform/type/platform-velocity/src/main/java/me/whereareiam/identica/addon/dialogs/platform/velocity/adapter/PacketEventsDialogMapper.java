package me.whereareiam.identica.addon.dialogs.platform.velocity.adapter;

import com.github.retrooper.packetevents.protocol.chat.clickevent.ClickEvent;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.ConfirmationDialog;
import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.NoticeDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.Action;
import com.github.retrooper.packetevents.protocol.dialog.action.DialogTemplate;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicRunCommandAction;
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
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.ChatSwitchListener;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.LoadingScreenAnswerListener;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds PacketEvents' dialog from the addon's: rendered text, its inputs, and a button per action. In the game, a
 * command button runs its command line as a template the client fills from the inputs. On the loading screen, where
 * the client runs no commands, a command button sends the custom click {@link LoadingScreenAnswerListener} reads
 * with the button's index, and buttons that run another command or switch to chat are left out. The button that
 * switches to chat in the game sends the custom click {@link ChatSwitchListener} reads.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PacketEventsDialogMapper {
	private static final int BUTTON_WIDTH = 150;
	private static final int BODY_WIDTH = 200;
	private static final int INPUT_WIDTH = 200;

	static com.github.retrooper.packetevents.protocol.dialog.Dialog of(Dialog dialog, DialogText text) {
		return of(dialog, text, false);
	}

	/**
	 * Builds the dialog for the game or for the loading screen.
	 *
	 * @param loadingScreen whether the player sees it on the loading screen
	 */
	static com.github.retrooper.packetevents.protocol.dialog.Dialog of(Dialog dialog, DialogText text, boolean loadingScreen) {
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
			button(dialog.getActions().get(index), index, text, loadingScreen).ifPresent(actions::add);
		ActionButton exit = dialog.getExitAction() == null
				? null
				: button(dialog.getExitAction(), dialog.getActions().size(), text, loadingScreen).orElse(null);
		return switch (dialog.getType()) {
			case NOTICE -> new NoticeDialog(common, actions.isEmpty() ? NoticeDialog.DEFAULT_ACTION : actions.getFirst());
			case CONFIRMATION -> new ConfirmationDialog(common, actions.getFirst(), exit != null ? exit : closing(dialog, text));
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

	/**
	 * Builds a button, or none when it cannot work where the player sees the dialog, such as a command that is
	 * unavailable or a button that switches to chat on the loading screen.
	 */
	private static Optional<ActionButton> button(
			me.whereareiam.identica.addon.dialogs.model.DialogAction action,
			int index,
			DialogText text,
			boolean loadingScreen
	) {
		CommonButtonData data = new CommonButtonData(text.render(action.getLabel()), null, BUTTON_WIDTH);
		String value = action.getValue();
		return switch (action.getType()) {
			case COMMAND -> Optional.ofNullable(text.command(action))
					.map(command -> new ActionButton(data, loadingScreen ? answer(index) : new DynamicRunCommandAction(new DialogTemplate(command))));
			case RUN -> loadingScreen
					? Optional.empty()
					: Optional.ofNullable(text.command(action)).map(command -> new ActionButton(data, new DynamicRunCommandAction(new DialogTemplate(command))));
			case URL -> Optional.of(new ActionButton(data, new StaticAction(ClickEvent.fromAdventure(net.kyori.adventure.text.event.ClickEvent.openUrl(value)))));
			case COPY -> Optional.of(new ActionButton(data, new StaticAction(ClickEvent.fromAdventure(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(value)))));
			case CLOSE -> Optional.of(new ActionButton(data, null));
			case CHAT -> loadingScreen
					? Optional.empty()
					: Optional.of(new ActionButton(data, new DynamicCustomAction(new ResourceLocation(ChatSwitchListener.CHAT_CLICK), new NBTCompound())));
		};
	}

	private static Action answer(int index) {
		NBTCompound additions = new NBTCompound();
		additions.setTag(LoadingScreenAnswerListener.ACTION, new NBTInt(index));
		return new DynamicCustomAction(new ResourceLocation(LoadingScreenAnswerListener.ANSWER_CLICK), additions);
	}

	/**
	 * A confirmation needs a second button; when its own cannot work on the loading screen, it only closes.
	 */
	private static ActionButton closing(Dialog dialog, DialogText text) {
		String label = dialog.getExitAction() != null ? dialog.getExitAction().getLabel() : "";
		return new ActionButton(new CommonButtonData(text.render(label), null, BUTTON_WIDTH), null);
	}
}
