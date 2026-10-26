package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.connection.ServerSwitchingEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.*;
import me.whereareiam.identica.type.message.ClientScreen;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Default Message Service")
class DefaultMessageServiceTest {
	private static final MessageDefinition<String> GREETING = MessageDefinition.<String>builder()
			.id("test:greeting")
			.text(document -> document)
			.placeholder("player")
			.build();

	private final Identity identity = mock(Identity.class);
	private final CommandService commands = mock(CommandService.class);
	private final List<String> shown = new ArrayList<>();
	private static final Prompt PASSWORD = Prompt.builder().id("test:password").command("login").build();
	private static final MessageDefinition<String> ASKED = GREETING.toBuilder().id("test:asked").prompt(PASSWORD).build();

	private boolean onLoadingScreen;
	private DefaultMessageService service;

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class))).thenAnswer(call -> {
			SerializerContent content = call.getArgument(0);
			String text = content.getMessage();
			for (Map.Entry<String, String> placeholder : content.getPlaceholders().entrySet())
				text = text.replace("{" + placeholder.getKey() + "}", placeholder.getValue());

			return Component.text(text);
		});
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void connectedPlayer() {
		when(identity.getConnectionUniqueId()).thenReturn(UUID.randomUUID());
		service = new DefaultMessageService(() -> connection -> onLoadingScreen, () -> commands, mock(EventManager.class));
		service.onServerJoined(new ServerJoinedEvent(identity, "lobby"));
	}

	@DisplayName("Shows a message without a presentation in chat, with its placeholders filled for the player")
	@Test
	void showsAMessageInChat() {
		service.send(identity, GREETING.create("Welcome, {player}", Map.of("player", "Alice")));

		verify(identity).sendMessage(Component.text("Welcome, Alice"));
	}

	@DisplayName("Shows nothing for a message whose text an admin cleared")
	@Test
	void showsNothingForAnEmptyMessage() {
		service.send(identity, GREETING.create(""));

		verify(identity, never()).sendMessage(any(Component.class));
	}

	@DisplayName("Shows chat and a title once a player on the loading screen joined a server, and the message's other parts at once")
	@Test
	void waitsWithWhatTheLoadingScreenCannotShow() {
		onLoadingScreen = true;
		service.registerPartType(recordingType("dialog"));
		service.present(ASKED.getId(), Presentation.builder().part(new TestPart("dialog", "Log in")).build());

		service.send(identity, ASKED.create("Password?").withTitle("Welcome", null));
		verify(identity, never()).sendMessage(any(Component.class));
		verify(identity, never()).sendTitle(any());
		assertEquals(List.of("dialog:Log in"), shown);

		onLoadingScreen = false;
		service.onServerJoined(new ServerJoinedEvent(identity, "lobby"));
		verify(identity).sendMessage(Component.text("Password?"));
		verify(identity).sendTitle(Title.title(Component.text("Welcome"), Component.empty()));
	}

	@DisplayName("Waits with chat until the client joined its first server, and while it switches servers")
	@Test
	void waitsWithChatUntilTheFirstJoin() {
		Identity joining = mock(Identity.class);
		when(joining.getConnectionUniqueId()).thenReturn(UUID.randomUUID());

		service.send(joining, GREETING.create("Welcome"));
		verify(joining, never()).sendMessage(any(Component.class));

		service.onServerJoined(new ServerJoinedEvent(joining, "lobby"));
		verify(joining).sendMessage(Component.text("Welcome"));
	}

	@DisplayName("Waits with chat while the client switches servers")
	@Test
	void waitsWithChatBetweenServers() {
		service.onServerSwitching(new ServerSwitchingEvent(identity));
		service.send(identity, GREETING.create("Welcome"));
		assertEquals(ClientScreen.JOINING, service.screen(identity.getConnectionUniqueId()));
		verify(identity, never()).sendMessage(any(Component.class));

		service.onServerJoined(new ServerJoinedEvent(identity, "lobby"));
		assertEquals(ClientScreen.WORLD, service.screen(identity.getConnectionUniqueId()));
		verify(identity).sendMessage(Component.text("Welcome"));
	}

	@DisplayName("Drops what waited of a replaced prompt the player saw in a dialog, but shows what they saw nowhere")
	@Test
	void showsWhatThePlayerSawNowhereAfterTheLoadingScreen() {
		onLoadingScreen = true;
		service.registerPartType(recordingType("dialog"));
		MessageDefinition<String> inDialog = ASKED.toBuilder().id("test:in-dialog").build();
		service.present(inDialog.getId(), Presentation.builder().part(new TestPart("dialog", "Log in")).build());
		service.send(identity, inDialog.create("Password?"));
		service.send(identity, ASKED.create("Wrong password"));
		service.send(identity, GREETING.create("First notice"));
		service.send(identity, GREETING.create("Second notice"));

		onLoadingScreen = false;
		service.onServerJoined(new ServerJoinedEvent(identity, "lobby"));

		verify(identity, never()).sendMessage(Component.text("Password?"));
		verify(identity).sendMessage(Component.text("Wrong password"));
		verify(identity).sendMessage(Component.text("First notice"));
		verify(identity).sendMessage(Component.text("Second notice"));
	}

	@DisplayName("Shows what a resolved journey asked on the loading screen when nothing else showed it")
	@Test
	void showsTheAnswersOfAResolvedJourneyOnceJoined() {
		onLoadingScreen = true;
		service.send(identity, ASKED.create("Wrong password"));
		UUID connectionUniqueId = identity.getConnectionUniqueId();
		ScenarioResolvedEvent resolved = mock(ScenarioResolvedEvent.class);
		when(resolved.getConnectionUniqueId()).thenReturn(connectionUniqueId);
		service.onScenarioResolved(resolved);
		service.send(identity, GREETING.create("Welcome back"));

		onLoadingScreen = false;
		service.onServerJoined(new ServerJoinedEvent(identity, "lobby"));

		verify(identity).sendMessage(Component.text("Wrong password"));
		verify(identity).sendMessage(Component.text("Welcome back"));
	}

	@DisplayName("Adds a presentation's part next to chat")
	@Test
	void addsAPresentedPartNextToChat() {
		service.registerPartType(recordingType("actionbar"));
		service.present(GREETING.getId(), Presentation.builder()
				.part(new TestPart("actionbar", "Log in"))
				.build());

		service.send(identity, GREETING.create("Welcome"));

		verify(identity).sendMessage(Component.text("Welcome"));
		assertEquals(List.of("actionbar:Log in"), shown);
	}

	@DisplayName("Shows only a presentation's part when it removes chat")
	@Test
	void replacesChat() {
		service.registerPartType(recordingType("dialog"));
		service.present(GREETING.getId(), Presentation.builder()
				.part(new TestPart("dialog", "Password"))
				.removed(ChatPart.TYPE)
				.build());

		service.send(identity, GREETING.create("Welcome"));

		verify(identity, never()).sendMessage(any(Component.class));
		assertEquals(List.of("dialog:Password"), shown);
	}

	@DisplayName("Lets the presentation with the higher priority decide a part, whatever the order they were registered in")
	@Test
	void higherPriorityWins() {
		service.registerPartType(recordingType("dialog"));
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("dialog", "addon")).priority(10).build());
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("dialog", "admin")).priority(0).build());

		service.send(identity, GREETING.create("Welcome"));

		assertEquals(List.of("dialog:addon"), shown);
	}

	@DisplayName("Presents every message that asks for the same thing, letting a presentation of the message's own ID win")
	@Test
	void presentsAPrompt() {
		Prompt password = Prompt.builder().id("test:password").command("login").build();
		MessageDefinition<String> asked = GREETING.toBuilder().id("test:asked").prompt(password).build();
		MessageDefinition<String> askedAgain = GREETING.toBuilder().id("test:asked-again").prompt(password).build();
		service.registerPartType(recordingType("dialog"));
		service.presentPrompt("TEST:password", Presentation.builder().part(new TestPart("dialog", "prompt")).removed(ChatPart.TYPE).build());
		service.present(askedAgain.getId(), Presentation.builder().part(new TestPart("dialog", "own")).build());

		service.send(identity, asked.create("Password?"));
		service.send(identity, askedAgain.create("Wrong password"));
		service.send(identity, GREETING.create("Welcome"));

		assertEquals(List.of("dialog:prompt", "dialog:own"), shown);
		verify(identity).sendMessage(Component.text("Welcome"));
		verify(identity, never()).sendMessage(Component.text("Password?"));
	}

	@DisplayName("Falls back to chat for a player whose client cannot show a part, unless chat shows the message anyway")
	@Test
	void fallsBackToChat() {
		service.registerPartType(PartType.<TestPart>builder()
				.id("dialog")
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> PartRendering.unsupported())
				.fallback(ChatPart.TYPE)
				.build());
		MessageService.Registration replacing = service.present(GREETING.getId(), Presentation.builder()
				.part(new TestPart("dialog", "Password"))
				.removed(ChatPart.TYPE)
				.build());

		service.send(identity, GREETING.create("Welcome"));
		replacing.remove();
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("dialog", "Password")).build());
		service.send(identity, GREETING.create("Welcome"));

		verify(identity, times(2)).sendMessage(Component.text("Welcome"));
	}

	@DisplayName("Keeps a prompt's part shown through notices until its journey resolves")
	@Test
	void keepsAPromptOpenThroughNotices() {
		AtomicInteger closed = new AtomicInteger();
		service.registerPartType(closingType("dialog", closed));
		service.presentPrompt(PASSWORD.getId(), Presentation.builder().part(new TestPart("dialog", "Log in")).build());

		service.send(identity, ASKED.create("Password?"));
		service.send(identity, GREETING.create("Welcome"));
		assertEquals(0, closed.get());

		UUID connectionUniqueId = identity.getConnectionUniqueId();
		ScenarioResolvedEvent resolved = mock(ScenarioResolvedEvent.class);
		when(resolved.getConnectionUniqueId()).thenReturn(connectionUniqueId);
		service.onScenarioResolved(resolved);
		assertEquals(1, closed.get());
	}

	@DisplayName("Ends the open prompt only when its owner names it, and everything when the player leaves")
	@Test
	void endsAPromptByItsOwner() {
		AtomicInteger closed = new AtomicInteger();
		service.registerPartType(closingType("dialog", closed));
		service.presentPrompt(PASSWORD.getId(), Presentation.builder().part(new TestPart("dialog", "Log in")).build());
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("dialog", "Notice")).build());

		service.send(identity, ASKED.create("Password?"));
		service.endPrompt(identity.getConnectionUniqueId(), Prompt.builder().id("test:other").build());
		assertEquals(0, closed.get());
		service.endPrompt(identity.getConnectionUniqueId(), PASSWORD);
		assertEquals(1, closed.get());

		service.send(identity, GREETING.create("Welcome"));
		service.onIdentityDetached(new IdentityDetachedEvent(identity.getConnectionUniqueId()));
		assertEquals(2, closed.get());
	}

	@DisplayName("Shows a prompt's part again after a server switch when the client drops it")
	@Test
	void showsADroppedPartAgainAfterAServerSwitch() {
		service.registerPartType(PartType.<TestPart>builder()
				.id("dialog")
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> {
					shown.add(message.getText());
					return PartRendering.shown(() -> {});
				})
				.droppedOnServerSwitch(true)
				.build());
		service.presentPrompt(PASSWORD.getId(), Presentation.builder().part(new TestPart("dialog", "")).build());

		service.send(identity, ASKED.create("Password?"));
		service.onServerJoined(new ServerJoinedEvent(identity, "lobby"));

		assertEquals(List.of("Password?", "Password?"), shown);
	}

	@DisplayName("Tells a message appended to another below it, keeping its own ID and placeholders")
	@Test
	void keepsAnAppendedMessageApart() {
		PlayerMessage warning = GREETING.toBuilder().id("test:warning").build().create("{attempts} attempts left", Map.of("attempts", "2"));
		PlayerMessage combined = GREETING.create("Wrong, {player}", Map.of("player", "Alice")).append(warning);

		service.send(identity, combined);

		assertEquals(List.of("test:greeting", "test:warning"), combined.sequence().stream().map(PlayerMessage::getId).toList());
		verify(identity).sendMessage(Component.join(JoinConfiguration.newlines(),
				Component.text("Wrong, Alice"), Component.text("2 attempts left")));
	}

	@DisplayName("Shows a message's title unless a presentation removes it")
	@Test
	void showsATitle() {
		service.send(identity, GREETING.create("Registered").withTitle("Welcome", "Have fun"));
		verify(identity).sendTitle(Title.title(Component.text("Welcome"), Component.text("Have fun")));

		service.present(GREETING.getId(), Presentation.builder().removed(TitlePart.TYPE).build());
		service.send(identity, GREETING.create("Registered").withTitle("Again", null));
		verify(identity, never()).sendTitle(Title.title(Component.text("Again"), Component.empty()));
	}

	@DisplayName("Takes the previous message's parts down only after the next message's parts are shown")
	@Test
	void closesAfterShowingTheNext() {
		List<String> events = new ArrayList<>();
		service.registerPartType(PartType.<TestPart>builder()
				.id("dialog")
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> {
					events.add("show " + message.getText());
					return PartRendering.shown(() -> events.add("close " + message.getText()));
				})
				.build());
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("dialog", "")).build());

		service.send(identity, GREETING.create("first"));
		service.send(identity, GREETING.create("second"));

		assertEquals(List.of("show first", "show second", "close first"), events);
	}

	@DisplayName("Keeps showing the message when one of its parts fails")
	@Test
	void survivesAFailingRenderer() {
		AtomicBoolean called = new AtomicBoolean();
		service.registerPartType(PartType.<TestPart>builder()
				.id("broken")
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> {
					called.set(true);
					throw new IllegalStateException("broken");
				})
				.build());
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("broken", "")).build());

		service.send(identity, GREETING.create("Welcome"));

		assertTrue(called.get());
		verify(identity).sendMessage(Component.text("Welcome"));
	}

	@DisplayName("Forgets the definitions and presentations a registration added once it is removed")
	@Test
	void removesWhatARegistrationAdded() {
		MessageService.Registration definitions = service.register(List.of(GREETING));
		assertEquals(Optional.of(GREETING), service.definition("TEST:Greeting"));

		service.registerPartType(recordingType("actionbar"));
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("actionbar", "Log in")).build()).remove();
		definitions.remove();

		service.send(identity, GREETING.create("Welcome"));
		assertTrue(service.definition(GREETING.getId()).isEmpty());
		assertTrue(shown.isEmpty());
	}

	@DisplayName("Knows Identica's own messages from the start")
	@Test
	void knowsCoreMessages() {
		assertTrue(service.definition("identica:provider-choice").isPresent());
	}

	@DisplayName("Builds the command line that answers a prompt from the command as a player types it")
	@Test
	void answersAPrompt() {
		when(commands.commandLine("login")).thenReturn(Optional.of("l"));
		Prompt prompt = Prompt.builder()
				.id("test:password")
				.field(Prompt.Field.of("password", Prompt.FieldType.SECRET))
				.command("login")
				.build();

		assertEquals(Optional.of("l hunter2"), service.answer(prompt, Map.of("password", "hunter2")));
		assertTrue(service.answer(prompt.toBuilder().command("missing").build(), Map.of()).isEmpty());
	}

	@DisplayName("Words a disconnect with the chat text of the message")
	@Test
	void wordsADisconnect() {
		assertEquals(Component.text("Bye, Alice"), service.disconnectReason(identity, GREETING.create("Bye, {player}", Map.of("player", "Alice"))));
	}

	private static PartType<TestPart> closingType(String id, AtomicInteger closed) {
		return PartType.<TestPart>builder()
				.id(id)
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> PartRendering.shown(closed::incrementAndGet))
				.build();
	}

	private PartType<TestPart> recordingType(String id) {
		return PartType.<TestPart>builder()
				.id(id)
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> {
					shown.add(part.type() + ":" + part.content());
					return PartRendering.shown();
				})
				.build();
	}

	private record TestPart(@NotNull String type, @NotNull String content) implements MessagePart {
	}
}
