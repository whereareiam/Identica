package me.whereareiam.identica.addon.dialogs.display.bungeecord;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.addon.dialogs.type.InputType;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.action.CustomClickAction;
import net.md_5.bungee.api.dialog.input.TextInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BungeeCordDialogMapperTest {
	private static final Dialog LOGIN = Dialog.builder()
			.id("login")
			.type(DialogType.MULTI_ACTION)
			.title("Identica")
			.body("Welcome back!")
			.body("{message}")
			.input(DialogInput.builder().key("password").type(InputType.TEXT).label("Password").maxLength(64).build())
			.action(new DialogAction("Log in", ActionType.COMMAND, "login $(password)"))
			.action(new DialogAction("Use chat instead", ActionType.CHAT, ""))
			.exitAction(new DialogAction("Leave", ActionType.DISCONNECT, "You left the server."))
			.afterAction(AfterAction.NONE)
			.build();

	@Test
	void buildsAMultiActionDialogWithItsInputsAndButtons() {
		MultiActionDialog dialog = assertInstanceOf(MultiActionDialog.class,
				BungeeCordDialogMapper.of(LOGIN, text -> text.equals("{message}") ? Component.empty() : Component.text(text)));

		assertEquals("Identica", dialog.getBase().title().toPlainText());
		assertEquals(DialogBase.AfterAction.NONE, dialog.getBase().afterAction());
		assertFalse(dialog.getBase().canCloseWithEscape());
		assertFalse(dialog.getBase().pause());
		assertEquals(1, dialog.getBase().body().size());

		TextInput password = assertInstanceOf(TextInput.class, dialog.getBase().inputs().getFirst());
		assertEquals("password", password.key());
		assertEquals(64, password.maxLength());

		CustomClickAction login = assertInstanceOf(CustomClickAction.class, dialog.actions().getFirst().action());
		assertEquals("identica:dialogs/command", login.id());
		assertEquals("login", login.additions().getAsJsonObject().get("identica:dialog").getAsString());
		assertEquals("0", login.additions().getAsJsonObject().get("identica:button").getAsString());
		assertEquals("identica:dialogs/chat", assertInstanceOf(CustomClickAction.class, dialog.actions().get(1).action()).id());

		CustomClickAction leave = assertInstanceOf(CustomClickAction.class, dialog.exitAction().action());
		assertEquals("identica:dialogs/disconnect", leave.id());
		assertEquals("login", leave.additions().getAsJsonObject().get("identica:dialog").getAsString());
		assertEquals("exit", leave.additions().getAsJsonObject().get("identica:button").getAsString());
	}
}
