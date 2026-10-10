package me.whereareiam.identica.adapter.command.executor.admin;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Sessions Command")
class SessionsCommandTest {
	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	@DisplayName("Ending a session asks for the player to be disconnected, with or without a message")
	@ParameterizedTest(name = "message \"{0}\"")
	@ValueSource(strings = {"", "Your session was ended"})
	void endAsksForDisconnect(String message) {
		UUID uniqueId = UUID.randomUUID();
		SessionService sessions = mock(SessionService.class);
		when(sessions.findAllByUniqueId(uniqueId))
				.thenReturn(CompletableFuture.completedFuture(List.of(Session.builder().uniqueId(uniqueId).build())));
		when(sessions.close(any(SessionCloseRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

		new SessionsCommand(
				() -> messages(message.isEmpty() ? List.of() : Arrays.asList(message.split("\n"))),
				Commands::new,
				mock(AccountPersistenceService.class),
				sessions,
				mock(IdentityService.class)
		).end(mock(Actor.class), uniqueId.toString());

		ArgumentCaptor<SessionCloseRequest> request = ArgumentCaptor.forClass(SessionCloseRequest.class);
		verify(sessions).close(request.capture());
		assertTrue(request.getValue().isDisconnect());
		assertEquals(message, request.getValue().getDisconnectMessage());
	}

	private static Messages messages(List<String> disconnect) {
		Messages.Commands.Admin.Sessions.End end = new Messages.Commands.Admin.Sessions.End();
		end.setEnded("ended");
		end.setNotFound("not-found");
		end.setDisconnect(disconnect);
		Messages.Commands.Admin.Sessions sessions = new Messages.Commands.Admin.Sessions();
		sessions.setUnknown("unknown");
		sessions.setEnd(end);
		Messages.Commands.Admin admin = new Messages.Commands.Admin();
		admin.setSessions(sessions);
		Messages.Commands commands = new Messages.Commands();
		commands.setAdmin(admin);
		Messages messages = new Messages();
		messages.setCommands(commands);
		return messages;
	}
}
