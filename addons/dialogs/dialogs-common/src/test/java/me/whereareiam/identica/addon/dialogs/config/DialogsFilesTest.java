package me.whereareiam.identica.addon.dialogs.config;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogsFilesTest {
	private final Configura configura = Config.builder().format(Format.YAML).build();

	@Test
	void writesTheShippedRulesAndDialogsOnTheFirstStart(@TempDir Path folder) {
		List<List<DialogRule>> read = new ArrayList<>();
		DialogsFiles files = new DialogsFiles(configura, folder, read::add);

		assertTrue(Files.exists(folder.resolve("rules.yml")));
		assertEquals(Set.of("login", "register", "register-confirm", "verification"), files.dialogs().keySet());
		assertEquals(DialogRule.builder().when("prompt", "credential:password").dialog("login").build(),
				files.rules().getFirst());
		assertEquals(4, files.rules().size());
		assertEquals(List.of(files.rules()), read);

		Dialog login = files.dialogs().get("login");
		assertEquals(List.of(new DialogAction("<green>Log in", ActionType.ANSWER, "")), login.getActions());
		assertEquals(new DialogAction("<white>Leave", ActionType.DISCONNECT, "<white>You left the server."), login.getExitAction());
		assertEquals(List.of("<white>Welcome back!", "<gray>Enter your password to continue to the server.", "{answer}"), login.getBody());
		assertEquals("", login.getTitle());
		assertEquals("<red>Wrong password. Try again.", login.getAnswers().get("credential:password-invalid"));
		assertEquals(List.of("credential:passwords-differ", "credential:no-new-password", "credential:password-too-short"),
				files.dialogs().get("register").getAnswers().keySet().stream().limit(3).toList());
		assertEquals("password", login.getInputs().getFirst().getKey());
		assertEquals(64, login.getInputs().getFirst().getMaxLength());
		assertEquals(AfterAction.NONE, login.getAfterAction());
		assertFalse(login.isCloseWithEscape());
	}

	@Test
	void keepsADeletedShippedDialogDeletedAndReadsAnAdministratorsOwn(@TempDir Path folder) throws Exception {
		DialogsFiles files = new DialogsFiles(configura, folder, rules -> {
		});
		Files.delete(folder.resolve("dialogs/verification.yml"));
		Files.writeString(folder.resolve("dialogs/rules.yml"), """
				type: NOTICE
				title: "Server rules"
				body:
				  - "Be nice."
				afterAction: CLOSE
				""");
		Files.writeString(folder.resolve("rules.yml"), """
				rules:
				  - when:
				      prompt: credential:new-password
				    dialog: rules
				  - when:
				      prompt: credential:password
				    dialog: none
				""");

		files.reload();

		assertEquals(Set.of("login", "register", "register-confirm", "rules"), files.dialogs().keySet());
		assertEquals("Server rules", files.dialogs().get("rules").getTitle());
		assertEquals(List.of("Be nice."), files.dialogs().get("rules").getBody());
		assertEquals("rules", files.rules().getFirst().getDialog());
		assertNull(files.rules().get(1).getDialog());
	}
}
