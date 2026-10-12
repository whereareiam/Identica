package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogAction;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogPresenterTest {
	private static final UUID ALICE = UUID.randomUUID();
	private static final Dialog LOGIN = Dialog.builder()
			.id("login")
			.type(DialogType.MULTI_ACTION)
			.title("login")
			.action(new DialogAction("Log in", ActionType.COMMAND, "login $(password)"))
			.exitAction(new DialogAction("Leave", ActionType.DISCONNECT, "Bye, {name}!"))
			.answer("credential:password-invalid", "Invalid password.")
			.afterAction(AfterAction.NONE)
			.build();
	private static final Dialog REGISTER = Dialog.builder()
			.id("register")
			.type(DialogType.NOTICE)
			.title("register")
			.answer("test:taken", "Taken.")
			.afterAction(AfterAction.CLOSE)
			.build();
	private static final List<DialogRule> RULES = List.of(
			DialogRule.builder().on("step-waiting").when("step", "password-authentication-password").dialog("login").build(),
			DialogRule.builder().on("step-waiting").when("step", "password-registration").dialog("register").build(),
			DialogRule.builder().on("step-waiting").when("step", "password-validation").dialog(null).build(),
			DialogRule.builder().on("step-waiting").when("step", "password-recognition").dialog("missing").build()
	);

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	private final RecordingPlatform platform = new RecordingPlatform();
	private final DialogPresenter presenter = new DialogPresenter(platform, platform, identities(), () -> RULES,
			() -> Map.of("login", LOGIN, "register", REGISTER));

	@Test
	void showsTheDialogOfTheFirstMatchingRuleWithItsTextForTheAnswer() {
		platform.supported.add(ALICE);
		presenter.joined(ALICE);

		presenter.fire(waiting("password-authentication-password", "credential:password-invalid"));
		presenter.fire(waiting("password-authentication-password", "credential:unknown"));

		assertEquals(List.of("show login {answer=Invalid password.}", "show login {answer=}"), platform.calls);
	}

	@Test
	void waitsUntilTheClientIsInAWorldAndShowsTheDialogAgainAfterEveryServerSwitch() {
		platform.supported.add(ALICE);
		presenter.fire(waiting("password-registration", ""));
		assertEquals(List.of(), platform.calls);

		presenter.joined(ALICE);
		presenter.switchingServer(ALICE);
		presenter.fire(waiting("password-registration", "test:taken"));
		presenter.joined(ALICE);

		assertEquals(List.of("show register {answer=}", "show register {answer=Taken.}"), platform.calls);
	}

	@Test
	void showsNoDialogToAClientThatCannotShowOne() {
		presenter.joined(ALICE);
		presenter.fire(waiting("password-registration", ""));

		assertEquals(List.of(), platform.calls);
	}

	@Test
	void closesTheDialogWhenTheMomentSettlesOrNoRuleShowsOne() {
		platform.supported.add(ALICE);
		presenter.joined(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.settle(ALICE, "other-trigger");
		presenter.settle(ALICE, "step-waiting");
		presenter.settle(ALICE, "step-waiting");

		presenter.fire(waiting("password-authentication-password", ""));
		presenter.fire(waiting("password-validation", ""));
		presenter.fire(waiting("password-recognition", ""));
		presenter.fire(waiting("password-unknown", ""));

		assertEquals(List.of("show register {answer=}", "clear", "show login {answer=}", "clear"), platform.calls);
	}

	@Test
	void showsNoMoreDialogsToAPlayerWhoSwitchedToChatUntilTheyLeave() {
		platform.supported.add(ALICE);
		presenter.joined(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.clicked(ALICE, DialogClicks.CHAT, Map.of());
		presenter.fire(waiting("password-registration", ""));
		presenter.show(ALICE, "login", Map.of());
		presenter.joined(ALICE);
		assertEquals(List.of("show register {answer=}", "clear"), platform.calls);

		presenter.left(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.joined(ALICE);
		assertEquals(List.of("show register {answer=}", "clear", "show register {answer=}"), platform.calls);
	}

	@Test
	void showsAndClearsDialogsDirectly() {
		platform.supported.add(ALICE);
		presenter.joined(ALICE);

		presenter.show(ALICE, "login", Map.of("message", "Hello"));
		presenter.clear(ALICE);
		presenter.clear(ALICE);

		assertEquals(List.of("show login {answer=, message=Hello}", "clear"), platform.calls);
		assertThrows(IllegalArgumentException.class, () -> presenter.show(ALICE, "missing", Map.of()));
	}

	/**
	 * A command button runs its command with the inputs filled in, but only a button of the dialog the player should
	 * see now: a click naming another dialog, a missing button or a button that is no command runs nothing.
	 */
	@Test
	void runsOnlyTheCommandButtonsOfTheDialogThePlayerShouldSee() {
		platform.supported.add(ALICE);
		presenter.joined(ALICE);
		presenter.fire(waiting("password-authentication-password", ""));

		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "0", "password", "Secret-123"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "register", "identica:button", "0"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "7"));
		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "exit"));
		presenter.clicked(ALICE, "identica:dialogs/unknown", Map.of("identica:dialog", "login", "identica:button", "0"));

		assertEquals(List.of("show login {answer=}", "run login Secret-123"), platform.calls);
	}

	/**
	 * The disconnect button disconnects the player with its text rendered for them, but only from the dialog the player
	 * should see now, and a command click cannot trigger it.
	 */
	@Test
	void disconnectsThroughTheDisconnectButtonOfTheDialogThePlayerShouldSee() {
		platform.supported.add(ALICE);
		presenter.joined(ALICE);
		presenter.show(ALICE, "login", Map.of("name", "Alice"));

		presenter.clicked(ALICE, DialogClicks.COMMAND, Map.of("identica:dialog", "login", "identica:button", "exit"));
		presenter.clicked(ALICE, DialogClicks.DISCONNECT, Map.of("identica:dialog", "register", "identica:button", "exit"));
		presenter.clicked(ALICE, DialogClicks.DISCONNECT, Map.of("identica:dialog", "login", "identica:button", "0"));
		presenter.clicked(ALICE, DialogClicks.DISCONNECT, Map.of("identica:dialog", "login", "identica:button", "exit"));

		assertEquals(List.of("show login {answer=, name=Alice}", "disconnect Bye, Alice!"), platform.calls);
	}

	private static TriggerContext waiting(String step, String answer) {
		TriggerContext.TriggerContextBuilder context = TriggerContext.builder()
				.connectionUniqueId(ALICE)
				.trigger("step-waiting")
				.attribute("step", step);
		if (!answer.isEmpty()) context.attribute("answer", answer);

		return context.build();
	}

	private static Dialog dialog(String id) {
		return Dialog.builder()
				.id(id)
				.type(DialogType.NOTICE)
				.title(id)
				.afterAction(AfterAction.CLOSE)
				.build();
	}

	private static IdentityService identities() {
		IdentityService identities = mock(IdentityService.class);
		when(identities.findByConnectionUniqueId(any())).thenReturn(Optional.empty());
		return identities;
	}

	/**
	 * The proxy and its display in one, recording the calls to both in the order the presenter makes them, with the
	 * placeholders a dialog would be rendered with.
	 */
	private static final class RecordingPlatform implements DialogPlatform, DialogDisplay {
		private final List<String> calls = new ArrayList<>();
		private final Set<UUID> supported = new HashSet<>();

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
