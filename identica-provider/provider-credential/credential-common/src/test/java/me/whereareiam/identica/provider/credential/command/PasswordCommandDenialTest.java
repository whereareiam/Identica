package me.whereareiam.identica.provider.credential.command;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The password commands disconnect a player whose attempt the pipeline denies, whatever the denial says.
 */
@DisplayName("Password Command Denials")
class PasswordCommandDenialTest {
	private final ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);
	private final Identity identity = mock(Identity.class);

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void connectedPlayer() {
		when(identity.getConnectionUniqueId()).thenReturn(UUID.randomUUID());
	}

	static Stream<ConnectionDecision> denials() {
		return Stream.of(
				ConnectionDecision.deny(""),
				ConnectionDecision.deny(null),
				ConnectionDecision.deny("Wrong password"),
				ConnectionDecision.requireReconnect(""),
				ConnectionDecision.requireReconnect("Join again")
		);
	}

	@DisplayName("/login disconnects on a denial with or without a reason")
	@ParameterizedTest(name = "{0}")
	@MethodSource("denials")
	void loginDisconnectsOnDenial(ConnectionDecision decision) {
		when(coordinator.advance(any())).thenReturn(CompletableFuture.completedFuture(decision));

		new LoginCommand(coordinator, CredentialMessages::new).login(identity, "secret");

		verify(identity).disconnect(Component.text(decision.getMessage() == null ? "" : decision.getMessage()));
	}

	@DisplayName("/pass and /passconfirm disconnect on a denial with or without a reason")
	@ParameterizedTest(name = "{0}")
	@MethodSource("denials")
	void passDisconnectsOnDenial(ConnectionDecision decision) {
		when(coordinator.advance(any())).thenReturn(CompletableFuture.completedFuture(decision));
		PassCommand command = new PassCommand(coordinator, CredentialMessages::new);

		command.pass(identity, "secret");
		command.passConfirm(identity, "secret");

		verify(identity, times(2)).disconnect(Component.text(decision.getMessage() == null ? "" : decision.getMessage()));
	}
}
