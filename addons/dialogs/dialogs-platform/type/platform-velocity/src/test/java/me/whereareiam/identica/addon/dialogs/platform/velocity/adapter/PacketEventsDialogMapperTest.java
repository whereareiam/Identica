package me.whereareiam.identica.addon.dialogs.platform.velocity.adapter;

import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicRunCommandAction;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessageDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.input.TextInputControl;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.addon.dialogs.type.InputType;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PacketEventsDialogMapperTest {
	private static final Dialog LOGIN = Dialog.builder()
			.id("login")
			.type(DialogType.MULTI_ACTION)
			.title("Identica")
			.body("Welcome back!")
			.body("{message}")
			.input(DialogInput.builder().key("password").type(InputType.TEXT).label("Password").maxLength(64).build())
			.action(new me.whereareiam.identica.addon.dialogs.model.DialogAction("Log in", ActionType.COMMAND, "login $(password)"))
			.action(new me.whereareiam.identica.addon.dialogs.model.DialogAction("Spawn", ActionType.COMMAND, "spawn"))
			.exitAction(new me.whereareiam.identica.addon.dialogs.model.DialogAction("Use chat instead", ActionType.CHAT, ""))
			.afterAction(AfterAction.NONE)
			.build();

	@Test
	void buildsAMultiActionDialogWithItsInputsAndButtons() {
		MultiActionDialog dialog = assertInstanceOf(MultiActionDialog.class,
				PacketEventsDialogMapper.of(LOGIN, text -> text.equals("{message}") ? Component.empty() : Component.text(text)));

		assertEquals(Component.text("Identica"), dialog.getCommon().getTitle());
		assertEquals(DialogAction.NONE, dialog.getCommon().getAfterAction());
		assertFalse(dialog.getCommon().isCanCloseWithEscape());
		assertFalse(dialog.getCommon().isPause());
		assertEquals(1, dialog.getCommon().getBody().size());
		assertEquals(Component.text("Welcome back!"),
				assertInstanceOf(PlainMessageDialogBody.class, dialog.getCommon().getBody().getFirst()).getMessage().getContents());

		TextInputControl password = assertInstanceOf(TextInputControl.class, dialog.getCommon().getInputs().getFirst().getControl());
		assertEquals("password", dialog.getCommon().getInputs().getFirst().getKey());
		assertEquals(64, password.getMaxLength());

		assertEquals("login $(password)",
				assertInstanceOf(DynamicRunCommandAction.class, dialog.getActions().getFirst().getAction()).getTemplate().getRaw());
		assertEquals("spawn", assertInstanceOf(DynamicRunCommandAction.class, dialog.getActions().get(1).getAction()).getTemplate().getRaw());
		assertEquals("identica:dialogs/chat",
				assertInstanceOf(DynamicCustomAction.class, dialog.getExitAction().getAction()).getId().toString());
		assertEquals(List.of(Component.text("Log in"), Component.text("Spawn")),
				dialog.getActions().stream().map(button -> button.getButton().getLabel()).toList());
	}
}
