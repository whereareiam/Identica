package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageGate;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.model.message.*;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
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
	private MessageGate gate = (recipient, message) -> false;
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
		service = new DefaultMessageService(() -> gate, () -> commands, mock(EventManager.class));
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

	@DisplayName("Leaves a message the gate holds back to the gate")
	@Test
	void leavesAHeldMessageToTheGate() {
		gate = (recipient, message) -> true;

		service.send(identity, GREETING.create("Welcome"));

		verify(identity, never()).sendMessage(any(Component.class));
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

	@DisplayName("Takes a shown part down when the next message arrives and when the player leaves")
	@Test
	void closesWhatIsShown() {
		AtomicInteger closed = new AtomicInteger();
		service.registerPartType(PartType.<TestPart>builder()
				.id("bossbar")
				.partClass(TestPart.class)
				.renderer((part, message, recipient) -> PartRendering.shown(closed::incrementAndGet))
				.build());
		service.present(GREETING.getId(), Presentation.builder().part(new TestPart("bossbar", "Log in")).build());

		service.send(identity, GREETING.create("Welcome"));
		service.send(identity, GREETING.create("Welcome"));
		assertEquals(1, closed.get());

		service.onIdentityDetached(new IdentityDetachedEvent(identity.getConnectionUniqueId()));
		assertEquals(2, closed.get());
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

	@DisplayName("Builds the command line that answers a prompt from the command's configured alias")
	@Test
	void answersAPrompt() {
		CommandDefinition login = CommandDefinition.builder().aliases(List.of("l", "login")).build();
		when(commands.getRegisteredDefinitions()).thenReturn(Map.of("login", login));
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
