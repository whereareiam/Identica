package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.platform.PlatformListener;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.identity.IdentityService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogPresenterTest {
	private static final UUID ALICE = UUID.randomUUID();
	private static final Dialog LOGIN = dialog("login");
	private static final Dialog REGISTER = dialog("register");
	private static final List<DialogRule> RULES = List.of(
			DialogRule.builder().on("step-waiting").when("step", "password-authentication-password").dialog("login").build(),
			DialogRule.builder().on("step-waiting").when("step", "password-registration").dialog("register").build(),
			DialogRule.builder().on("step-waiting").when("step", "password-validation").dialog(null).build(),
			DialogRule.builder().on("step-waiting").when("step", "password-recognition").dialog("missing").build()
	);

	private final RecordingPlatform platform = new RecordingPlatform();
	private final DialogPresenter presenter = new DialogPresenter(platform, identities(), () -> RULES,
			() -> Map.of("login", LOGIN, "register", REGISTER));

	@Test
	void showsTheDialogOfTheFirstMatchingRuleWithTheMomentsPlaceholders() {
		platform.supported.add(ALICE);

		presenter.fire(waiting("password-authentication-password", "{prefix}Invalid password."));

		assertEquals(List.of("show login {message={prefix}Invalid password.}"), platform.calls);
	}

	@Test
	void waitsUntilThePlayerConnectsAndShowsTheDialogAgainAfterEveryServerSwitch() {
		presenter.fire(waiting("password-registration", ""));
		assertEquals(List.of(), platform.calls);

		platform.supported.add(ALICE);
		presenter.connected(ALICE);
		presenter.connected(ALICE);

		assertEquals(List.of("show register {message=}", "show register {message=}"), platform.calls);
	}

	@Test
	void closesTheDialogWhenTheMomentSettlesOrNoRuleShowsOne() {
		platform.supported.add(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.settle(ALICE, "other-trigger");
		presenter.settle(ALICE, "step-waiting");
		presenter.settle(ALICE, "step-waiting");

		presenter.fire(waiting("password-authentication-password", ""));
		presenter.fire(waiting("password-validation", ""));
		presenter.fire(waiting("password-recognition", ""));
		presenter.fire(waiting("password-unknown", ""));

		assertEquals(List.of("show register {message=}", "clear", "show login {message=}", "clear"), platform.calls);
	}

	@Test
	void showsNoMoreDialogsToAPlayerWhoSwitchedToChatUntilTheyLeave() {
		platform.supported.add(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.switchedToChat(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.show(ALICE, "login", Map.of());
		presenter.connected(ALICE);
		assertEquals(List.of("show register {message=}", "clear"), platform.calls);

		presenter.left(ALICE);
		presenter.fire(waiting("password-registration", ""));
		assertEquals(List.of("show register {message=}", "clear", "show register {message=}"), platform.calls);
	}

	@Test
	void showsAndClearsDialogsDirectly() {
		platform.supported.add(ALICE);

		presenter.show(ALICE, "login", Map.of("message", "Hello"));
		presenter.clear(ALICE);
		presenter.clear(ALICE);

		assertEquals(List.of("show login {message=Hello}", "clear"), platform.calls);
		assertThrows(IllegalArgumentException.class, () -> presenter.show(ALICE, "missing", Map.of()));
	}

	private static TriggerContext waiting(String step, String message) {
		return TriggerContext.builder()
				.connectionUniqueId(ALICE)
				.trigger("step-waiting")
				.attribute("step", step)
				.placeholder("message", message)
				.build();
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
	 * Records what the presenter asks the proxy to do, with the placeholders the dialog would be rendered with.
	 */
	private static final class RecordingPlatform implements DialogPlatform {
		private final List<String> calls = new ArrayList<>();
		private final Set<UUID> supported = new HashSet<>();

		@Override
		public void start(@NotNull PlatformListener listener) {
		}

		@Override
		public boolean supports(@NotNull UUID connectionUniqueId) {
			return supported.contains(connectionUniqueId);
		}

		@Override
		public void show(@NotNull UUID connectionUniqueId, @NotNull Dialog dialog, @NotNull DialogText text) {
			calls.add("show " + dialog.getId() + " " + ((PlayerText) text).placeholders());
		}

		@Override
		public void clear(@NotNull UUID connectionUniqueId) {
			calls.add("clear");
		}
	}
}
