package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.model.LoadingScreenRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.DialogText;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
	private LoadingScreenRule loadingScreen = LoadingScreenRule.builder().enabled(true).build();
	private final DialogPresenter presenter = new DialogPresenter(platform, identities(), mock(CommandService.class), () -> RULES,
			() -> loadingScreen, () -> Map.of("login", LOGIN, "register", REGISTER));

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
	void closesTheDialogWhenTheMomentSettlesAndTellsThePlatformWhenNoDialogAsks() {
		platform.supported.add(ALICE);
		presenter.fire(waiting("password-registration", ""));
		presenter.settle(ALICE, "other-trigger");
		presenter.settle(ALICE, "step-waiting");
		presenter.settle(ALICE, "step-waiting");

		presenter.fire(waiting("password-authentication-password", ""));
		presenter.fire(waiting("password-validation", ""));
		presenter.fire(waiting("password-recognition", ""));
		presenter.fire(waiting("password-unknown", ""));

		assertEquals(List.of("show register {message=}", "clear", "show login {message=}", "clear", "unasked", "unasked", "unasked"),
				platform.calls);
	}

	@Test
	void asksOnTheLoadingScreenWhenTheRuleMatchesTheConnection() {
		assertTrue(presenter.beforeJoin(request("credential", "play.example.com")));

		loadingScreen = LoadingScreenRule.builder().enabled(true).when("provider", "Credential").when("host", "play.example.com").build();
		assertTrue(presenter.beforeJoin(request("credential", "play.example.com")));
		assertFalse(presenter.beforeJoin(request("premium", "play.example.com")));
		assertFalse(presenter.beforeJoin(request("credential", "other.example.com")));

		loadingScreen = LoadingScreenRule.builder().enabled(false).build();
		assertFalse(presenter.beforeJoin(request("credential", "play.example.com")));
	}

	@Test
	void asksNoPlayerWhoSwitchedToChatOnTheLoadingScreen() {
		presenter.switchedToChat(ALICE);

		assertFalse(presenter.beforeJoin(request("credential", "play.example.com")));
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

	private static CheckpointRequest request(String provider, String host) {
		ConnectionIdentity identity = new ConnectionIdentity("Alice", "127.0.0.1");
		identity.setOrigin(new ConnectionIdentity.Origin(host, 25565));
		ProviderContext providerContext = mock(ProviderContext.class);
		when(providerContext.getProviderId()).thenReturn(provider);
		ScenarioContext context = mock(ScenarioContext.class);
		when(context.getIdentity()).thenReturn(identity);
		when(context.getProvider()).thenReturn(providerContext);
		return new CheckpointRequest(ALICE, context, PipelineType.AUTHENTICATION, StageType.PROVIDER, mock(Step.class), StepResult.waiting(""));
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
		public void start(@NotNull DialogPlatform.Listener listener) {
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

		@Override
		public void unasked(@NotNull UUID connectionUniqueId) {
			calls.add("unasked");
		}
	}
}
