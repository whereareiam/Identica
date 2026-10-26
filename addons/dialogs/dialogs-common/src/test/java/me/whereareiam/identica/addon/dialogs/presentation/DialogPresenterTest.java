package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.type.message.ClientScreen;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogPresenterTest {
	private static final UUID ALICE = UUID.randomUUID();
	private static final Prompt PASSWORD = Prompt.builder()
			.id("credential:password")
			.field(Prompt.Field.of("password", Prompt.FieldType.SECRET))
			.command("login")
			.build();
	private static final Dialog LOGIN = Dialog.builder()
			.id("login")
			.type(DialogType.MULTI_ACTION)
			.title("login")
			.action(new DialogAction("Log in", ActionType.ANSWER, ""))
			.action(new DialogAction("Log in again", ActionType.COMMAND, "login $(password)"))
			.exitAction(new DialogAction("Leave", ActionType.DISCONNECT, "Bye, {name}!"))
			.answer("credential:password-invalid", "Invalid password.")
			.afterAction(AfterAction.NONE)
			.build();
	private static final Dialog REGISTER = Dialog.builder()
			.id("register")
			.type(DialogType.NOTICE)
			.title("register")
			.afterAction(AfterAction.CLOSE)
			.build();

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	private final RecordingPlatform platform = new RecordingPlatform();
	private final DialogPresenter presenter = new DialogPresenter(platform, platform, identities(), messages(),
			() -> Map.of("login", LOGIN, "register", REGISTER));

	@Test
	void showsTheDialogWithTheMessageItsPlaceholdersAndTheDialogsTextForIt() {
		platform.supported.add(ALICE);

		presenter.want(ALICE, "login", message("credential:password-invalid", "Wrong password, {name}.", Map.of("name", "Alice")));
		presenter.want(ALICE, "login", message("credential:password", "Password?", Map.of()));

		assertEquals(List.of(
				"show login {answer=Invalid password., message=Wrong password, Alice., name=Alice}",
				"show login {answer=, message=Password?}"
		), platform.calls);
	}

	/**
	 * A dialog a plugin shows directly waits until the client is in a server's world, and shows again after a switch;
	 * the dialog of a message is Identica's to show again.
	 */
	@Test
	void showsADirectDialogOnceTheClientIsInAWorldAndAgainAfterASwitch() {
		MessageService messages = messages();
		when(messages.screen(ALICE)).thenReturn(ClientScreen.JOINING);
		DialogPresenter presenter = new DialogPresenter(platform, platform, identities(), messages, () -> Map.of("register", REGISTER));
		platform.supported.add(ALICE);

		presenter.show(ALICE, "register", Map.of());
		assertEquals(List.of(), platform.calls);

		presenter.joined(ALICE);
		presenter.joined(ALICE);
		assertEquals(List.of("show register {answer=}", "show register {answer=}"), platform.calls);
	}

	/**
	 * The dialog's text for each message told together, such as a wrong password and a warning appended to it, shows
	 * on its own line.
	 */
	@Test
	void answersEveryMessageToldTogether() {
		platform.supported.add(ALICE);
		PlayerMessage warning = PlayerMessage.builder().id("sentinel:attempts-left").text("{attempts} left").placeholders(new LinkedHashMap<>(Map.of("attempts", "2"))).build();
		Dialog login = Dialog.builder()
				.id("login")
				.type(DialogType.MULTI_ACTION)
				.title("login")
				.answer("credential:password-invalid", "Invalid password.")
				.answer("sentinel:attempts-left", "Careful.")
				.afterAction(AfterAction.NONE)
				.build();
		DialogPresenter presenter = new DialogPresenter(platform, platform, identities(), messages(), () -> Map.of("login", login));

		presenter.want(ALICE, "login", message("credential:password-invalid", "Wrong.", Map.of()).append(warning));

		assertEquals(List.of("show login {answer=Invalid password.\nCareful., message=Wrong.\n2 left}"), platform.calls);
	}

	@Test
	void showsNoDialogToAClientThatCannotShowOne() {
		presenter.want(ALICE, "register", message("credential:new-password", "", Map.of()));

		assertEquals(List.of(), platform.calls);
	}

	/**
	 * Identica takes a message down after the next one is shown, so the release of a message shown earlier leaves the
	 * dialog of the next one open, even when it is the same message again.
	 */
	@Test
	void closesTheDialogOnlyWhenItsOwnMessageIsTakenDown() {
		platform.supported.add(ALICE);
		PlayerMessage asked = message("credential:password", "Password?", Map.of());

		DialogPresenter.Wanted first = presenter.want(ALICE, "login", asked);
		DialogPresenter.Wanted again = presenter.want(ALICE, "login", asked);
		presenter.release(ALICE, first);
		presenter.release(ALICE, again);
		presenter.release(ALICE, again);

		assertEquals(List.of("show login {answer=, message=Password?}", "show login {answer=, message=Password?}", "clear"), platform.calls);
	}

	@Test
	void showsNoMoreDialogsToAPlayerWhoSwitchedToChatUntilTheyLeave() {
		platform.supported.add(ALICE);
		presenter.want(ALICE, "register", message("credential:new-password", "", Map.of()));
		presenter.clicked(ALICE, DialogClicks.CHAT, Map.of());
		presenter.show(ALICE, "login", Map.of());
		assertEquals(List.of("show register {answer=, message=}", "clear"), platform.calls);
		assertEquals(false, presenter.shows(ALICE));

		presenter.left(ALICE);
		assertEquals(true, presenter.shows(ALICE));
	}

	@Test
	void tellsAPlayerWhoSwitchesToChatTheMessageTheirDialogShowed() {
		IdentityService identities = mock(IdentityService.class);
		Identity alice = mock(Identity.class);
		when(identities.findByConnectionUniqueId(ALICE)).thenReturn(Optional.of(alice));
		MessageService messages = messages();
		DialogPresenter presenter = new DialogPresenter(platform, platform, identities, messages, () -> Map.of("login", LOGIN));
		platform.supported.add(ALICE);
		PlayerMessage asked = message("credential:password", "Password?", Map.of());

		presenter.want(ALICE, "login", asked);
		presenter.clicked(ALICE, DialogClicks.CHAT, Map.of());

		verify(messages).send(alice, asked);
	}

	@Test
	void showsAndClearsDialogsDirectly() {
		platform.supported.add(ALICE);

		presenter.show(ALICE, "login", Map.of("greeting", "Hello"));
		presenter.clear(ALICE);
		presenter.clear(ALICE);

		assertEquals(List.of("show login {answer=, greeting=Hello}", "clear"), platform.calls);
		assertThrows(IllegalArgumentException.class, () -> presenter.show(ALICE, "missing", Map.of()));
	}

	/**
	 * An answer button answers the prompt of the message the dialog shows and a command button runs its command, both
	 * with the inputs filled in, but only buttons of the dialog the player should see now: a click naming another
	 * dialog, a missing button or a button that is no command or answer runs nothing.
	 */
	@Test
	void runsOnlyTheAnswerAndCommandButtonsOfTheDialogThePlayerShouldSee() {
		platform.supported.add(ALICE);
		presenter.want(ALICE, "login", message("credential:password", "Password?", Map.of()));

		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "0", "password", "Secret-123"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "1", "password", "Other-456"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "register", "identica:button", "0"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "7"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "exit"));
		presenter.clicked(ALICE, "identica:dialogs/unknown", Map.of("identica:dialog", "login", "identica:button", "0"));

		assertEquals(List.of("show login {answer=, message=Password?}", "run login Secret-123", "run login Other-456"), platform.calls);
	}

	/**
	 * A dialog shown directly shows no message, so its answer button has no prompt to answer.
	 */
	@Test
	void answersNothingForADialogShownDirectly() {
		platform.supported.add(ALICE);
		presenter.show(ALICE, "login", Map.of());

		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "0", "password", "Secret-123"));

		assertEquals(List.of("show login {answer=}"), platform.calls);
	}

	/**
	 * The disconnect button disconnects the player with its text rendered for them, but only from the dialog the player
	 * should see now, and a command click cannot trigger it.
	 */
	@Test
	void disconnectsThroughTheDisconnectButtonOfTheDialogThePlayerShouldSee() {
		platform.supported.add(ALICE);
		presenter.show(ALICE, "login", Map.of("name", "Alice"));

		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "exit"));
		presenter.clicked(ALICE, DialogClicks.DISCONNECT, Map.of("identica:dialog", "register", "identica:button", "exit"));
		presenter.clicked(ALICE, DialogClicks.DISCONNECT, Map.of("identica:dialog", "login", "identica:button", "0"));
		presenter.clicked(ALICE, DialogClicks.DISCONNECT, Map.of("identica:dialog", "login", "identica:button", "exit"));

		assertEquals(List.of("show login {answer=, name=Alice}", "disconnect Bye, Alice!"), platform.calls);
	}

	private static PlayerMessage message(String id, String text, Map<String, String> placeholders) {
		return PlayerMessage.builder()
				.id(id)
				.text(text)
				.placeholders(new LinkedHashMap<>(placeholders))
				.prompt(PASSWORD)
				.build();
	}

	private static IdentityService identities() {
		IdentityService identities = mock(IdentityService.class);
		when(identities.findByConnectionUniqueId(any())).thenReturn(Optional.empty());
		return identities;
	}

	private static MessageService messages() {
		MessageService messages = mock(MessageService.class);
		when(messages.screen(any())).thenReturn(ClientScreen.WORLD);
		when(messages.answer(eq(PASSWORD), any()))
				.thenAnswer(call -> Optional.of("login " + call.<Map<String, String>>getArgument(1).get("password")));
		return messages;
	}

	/**
	 * The proxy and its display in one, recording the calls to both in the order the presenter makes them, with the
	 * placeholders a dialog would be rendered with.
	 */
	static final class RecordingPlatform implements DialogPlatform, DialogDisplay {
		final List<String> calls = new ArrayList<>();
		final Set<UUID> supported = new HashSet<>();

		@Override
		public void start(@NotNull DialogPlatform.Listener listener) {
		}

		@Override
		public boolean supports(@NotNull UUID connectionUniqueId) {
			return supported.contains(connectionUniqueId);
		}

		@Override
		public @NotNull DialogDisplay display() {
			return this;
		}

		@Override
		public void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text) {
			calls.add("show " + dialog.getId() + " " + new TreeMap<>(((PlayerText) text).placeholders()));
		}

		@Override
		public void clear(@NotNull UUID connectionUniqueId) {
			calls.add("clear");
		}

		@Override
		public void run(@NotNull UUID connectionUniqueId, @NotNull String command) {
			calls.add("run " + command);
		}

		@Override
		public void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component reason) {
			calls.add("disconnect " + PlainTextComponentSerializer.plainText().serialize(reason));
		}
	}
}
