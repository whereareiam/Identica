package me.whereareiam.identica.adapter.command.executor;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Enroll Command")
class EnrollCommandTest {
	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	static Stream<ConnectionDecision> denials() {
		return Stream.of(
				ConnectionDecision.deny(""),
				ConnectionDecision.deny(null),
				ConnectionDecision.deny("Registration failed"),
				ConnectionDecision.requireReconnect(""),
				ConnectionDecision.requireReconnect("Join again")
		);
	}

	@DisplayName("Disconnects on a denial with or without a reason")
	@ParameterizedTest(name = "{0}")
	@MethodSource("denials")
	void disconnectsOnDenial(ConnectionDecision decision) {
		ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);
		when(coordinator.resume(any())).thenReturn(CompletableFuture.completedFuture(decision));
		Identity identity = mock(Identity.class);
		when(identity.getUniqueId()).thenReturn(UUID.randomUUID());

		new EnrollCommand(coordinator, Engine::new, EnrollCommandTest::messages).enroll(identity, "credential");

		verify(identity).disconnect(Component.text(decision.getMessage() == null ? "" : decision.getMessage()));
	}

	private static Messages messages() {
		Messages messages = new Messages();
		Messages.Commands commands = new Messages.Commands();
		commands.setEnroll(new Messages.Commands.Enroll());
		messages.setCommands(commands);
		return messages;
	}
}
