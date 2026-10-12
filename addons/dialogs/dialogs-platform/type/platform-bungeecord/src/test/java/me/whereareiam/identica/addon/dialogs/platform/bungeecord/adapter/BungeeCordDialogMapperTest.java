package me.whereareiam.identica.addon.dialogs.platform.bungeecord.adapter;

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
import net.md_5.bungee.api.dialog.action.RunCommandAction;
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
			.exitAction(new DialogAction("Use chat instead", ActionType.CHAT, ""))
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

		assertEquals("login $(password)", assertInstanceOf(RunCommandAction.class, dialog.actions().getFirst().action()).template());
		assertEquals("identica:dialogs/chat", assertInstanceOf(CustomClickAction.class, dialog.exitAction().action()).id());
	}
}
