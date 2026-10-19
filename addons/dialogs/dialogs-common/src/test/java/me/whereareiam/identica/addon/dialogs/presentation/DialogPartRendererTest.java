package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.pipeline.PendingScenarioService;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import me.whereareiam.identica.type.message.ClientScreen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogPartRendererTest {
	private static final UUID ALICE = UUID.randomUUID();
	private static final Prompt PASSWORD = Prompt.builder().id("credential:password").command("login").build();
	private static final List<DialogRule> RULES = List.of(
			DialogRule.builder().when("message", "credential:password-invalid").dialog("wrong").build(),
			DialogRule.builder().when("prompt", "credential:password").when("host", "event.example.net").dialog("event").build(),
			DialogRule.builder().when("prompt", "credential:password").when("scenario", "REGISTRATION").dialog(null).build(),
			DialogRule.builder().when("prompt", "credential:password").dialog("login").build(),
			DialogRule.builder().when("prompt", "test:missing").dialog("missing").build()
	);

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	private final DialogPresenterTest.RecordingPlatform platform = new DialogPresenterTest.RecordingPlatform();
	private final PendingScenarioService scenarios = mock(PendingScenarioService.class);
	private final Identity alice = mock(Identity.class);
	private final MessageService messages = mock(MessageService.class);
	private DialogPresenter presenter;
	private DialogPartRenderer renderer;

	@BeforeEach
	void connectedPlayer() {
		IdentityService identities = mock(IdentityService.class);
		when(identities.findByConnectionUniqueId(any())).thenReturn(Optional.empty());
		Map<String, Dialog> dialogs = Map.of("login", dialog("login"), "event", dialog("event"), "wrong", dialog("wrong"), "register", dialog("register"));
		when(messages.screen(any())).thenReturn(ClientScreen.WORLD);
		DialogChooser chooser = new DialogChooser(scenarios, () -> RULES, () -> dialogs);
		presenter = new DialogPresenter(platform, platform, identities, messages, chooser, () -> dialogs);
		renderer = new DialogPartRenderer(presenter, chooser);
		when(alice.getConnectionUniqueId()).thenReturn(ALICE);
		when(scenarios.find(ALICE)).thenReturn(Optional.of(PipelineType.AUTHENTICATION));
		platform.supported.add(ALICE);
	}

	@Test
	void showsTheDialogTheFirstMatchingRulePicksAndClosesItWhenIdenticaTakesTheMessageDown() {
		PartRendering rendering = renderer.render(DialogPart.byRules(), message("credential:password"), alice);

		assertTrue(rendering.isShown());
		assertNotNull(rendering.getClose());
		rendering.getClose().close();
		assertEquals(List.of("show login {answer=, message=}", "clear"), platform.calls);
	}

	@Test
	void waitsWhileTheClientIsNotInAWorld() {
		when(messages.screen(ALICE)).thenReturn(ClientScreen.JOINING);

		assertTrue(renderer.render(DialogPart.byRules(), message("credential:password"), alice).isNotYet());
		assertEquals(List.of(), platform.calls);
	}

	@Test
	void matchesTheMessageTheHostAndTheScenario() {
		renderer.render(DialogPart.byRules(), message("credential:password-invalid"), alice);
		when(alice.getOrigin()).thenReturn(new ConnectionIdentity.Origin("Event.Example.Net", 25565));
		renderer.render(DialogPart.byRules(), message("credential:password"), alice);

		assertEquals(List.of("show wrong {answer=, message=}", "show event {answer=, message=}"), platform.calls);
	}

	/**
	 * Without a dialog, the part type falls back to chat: for a rule that shows none, a dialog no file defines, a player
	 * who switched to chat and a client that cannot show dialogs.
	 */
	@Test
	void leavesTheMessageToChatWhenNoDialogShowsIt() {
		when(scenarios.find(ALICE)).thenReturn(Optional.of(PipelineType.REGISTRATION));
		assertFalse(renderer.render(DialogPart.byRules(), message("credential:password"), alice).isShown());
		assertFalse(renderer.render(DialogPart.byRules(), message("test:missing"), alice).isShown());
		assertFalse(renderer.render(DialogPart.byRules(), message("test:unknown"), alice).isShown());

		platform.supported.remove(ALICE);
		assertFalse(renderer.render(DialogPart.of("login"), message("credential:password"), alice).isShown());
		assertEquals(List.of(), platform.calls);
	}

	@Test
	void showsTheDialogAPartNamesWithoutAskingTheRules() {
		renderer.render(DialogPart.of("register"), message("test:unknown"), alice);

		assertEquals(List.of("show register {answer=, message=}"), platform.calls);
	}

	private static PlayerMessage message(String id) {
		boolean asks = id.startsWith("credential:password") || id.equals("test:missing");
		return PlayerMessage.builder()
				.id(id)
				.prompt(asks ? (id.equals("test:missing") ? PASSWORD.toBuilder().id("test:missing").build() : PASSWORD) : null)
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
}
