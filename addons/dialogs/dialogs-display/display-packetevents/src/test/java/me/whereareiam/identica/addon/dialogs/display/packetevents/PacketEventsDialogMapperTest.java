package me.whereareiam.identica.addon.dialogs.display.packetevents;

import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
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
			.action(new me.whereareiam.identica.addon.dialogs.model.DialogAction("Use chat instead", ActionType.CHAT, ""))
			.exitAction(new me.whereareiam.identica.addon.dialogs.model.DialogAction("Leave", ActionType.DISCONNECT, "You left the server."))
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

		DynamicCustomAction login = assertInstanceOf(DynamicCustomAction.class, dialog.getActions().getFirst().getAction());
		assertEquals("identica:dialogs/command", login.getId().toString());
		assertEquals("login", login.getAdditions().getStringTagValueOrNull("identica:dialog"));
		assertEquals("0", login.getAdditions().getStringTagValueOrNull("identica:button"));
		assertEquals("1", assertInstanceOf(DynamicCustomAction.class, dialog.getActions().get(1).getAction())
				.getAdditions().getStringTagValueOrNull("identica:button"));
		assertEquals("identica:dialogs/chat",
				assertInstanceOf(DynamicCustomAction.class, dialog.getActions().get(2).getAction()).getId().toString());

		DynamicCustomAction leave = assertInstanceOf(DynamicCustomAction.class, dialog.getExitAction().getAction());
		assertEquals("identica:dialogs/disconnect", leave.getId().toString());
		assertEquals("login", leave.getAdditions().getStringTagValueOrNull("identica:dialog"));
		assertEquals("exit", leave.getAdditions().getStringTagValueOrNull("identica:button"));
		assertEquals(List.of(Component.text("Log in"), Component.text("Spawn"), Component.text("Use chat instead")),
				dialog.getActions().stream().map(button -> button.getButton().getLabel()).toList());
	}
}
