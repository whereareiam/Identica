package me.whereareiam.identica.addon.dialogs.platform.bungeecord.adapter;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogInput;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

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
			.action(new DialogAction("Log in", ActionType.COMMAND, "login", List.of("$(password)")))
			.action(new DialogAction("Spawn", ActionType.RUN, "spawn", List.of()))
			.exitAction(new DialogAction("Use chat instead", ActionType.CHAT, "", List.of()))
			.afterAction(AfterAction.NONE)
			.build();

	@Test
	void buildsAMultiActionDialogWithItsInputsAndButtons() {
		MultiActionDialog dialog = assertInstanceOf(MultiActionDialog.class,
				BungeeCordDialogMapper.of(LOGIN, TEXT));

		assertEquals("Identica", dialog.getBase().title().toPlainText());
		assertEquals(DialogBase.AfterAction.NONE, dialog.getBase().afterAction());
		assertFalse(dialog.getBase().canCloseWithEscape());
		assertFalse(dialog.getBase().pause());
		assertEquals(1, dialog.getBase().body().size());

		TextInput password = assertInstanceOf(TextInput.class, dialog.getBase().inputs().getFirst());
		assertEquals("password", password.key());
		assertEquals(64, password.maxLength());

		assertEquals("login $(password)", assertInstanceOf(RunCommandAction.class, dialog.actions().getFirst().action()).template());
		assertEquals("spawn", assertInstanceOf(RunCommandAction.class, dialog.actions().get(1).action()).template());
		assertEquals("identica:dialogs/chat", assertInstanceOf(CustomClickAction.class, dialog.exitAction().action()).id());
	}

	@Test
	void leavesOutTheButtonOfAnUnavailableCommand() {
		MultiActionDialog dialog = assertInstanceOf(MultiActionDialog.class, BungeeCordDialogMapper.of(LOGIN, text -> Component.text(text)));

		assertEquals(1, dialog.actions().size());
		assertEquals("spawn", assertInstanceOf(RunCommandAction.class, dialog.actions().getFirst().action()).template());
	}

	/**
	 * Renders text as is, without the message placeholder, and knows the login command by its definition.
	 */
	private static final DialogText TEXT = new DialogText() {
		@Override
		public @NotNull Component render(@NotNull String text) {
			return text.equals("{message}") ? Component.empty() : Component.text(text);
		}

		@Override
		public @Nullable String command(@NotNull DialogAction action) {
			if (action.getType() == ActionType.COMMAND && action.getValue().equals("login")) return "login $(password)";
			return DialogText.super.command(action);
		}
	};
}
