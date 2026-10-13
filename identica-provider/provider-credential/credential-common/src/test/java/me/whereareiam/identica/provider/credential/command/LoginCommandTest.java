package me.whereareiam.identica.provider.credential.command;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialMessagesDefaults;
import me.whereareiam.identica.provider.credential.pipeline.CredentialAuthenticationAttempt;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A player may type {@code /login} while another run of the connection, such as the one that shows the prompt on
 * joining, still holds the journey's state. The command leaves it to the pipeline to find the journey in the
 * connection's turn.
 */
@DisplayName("Login Command")
class LoginCommandTest {
	private final CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
	private final ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);
	private final Identity identity = mock(Identity.class);
	private final LoginCommand command = new LoginCommand(coordinator, () -> messages);

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

	@DisplayName("Hands a password typed before its prompt to the pipeline instead of looking for the journey itself")
	@Test
	void handsThePasswordToThePipeline() {
		when(coordinator.advance(any())).thenReturn(CompletableFuture.completedFuture(ConnectionDecision.waiting("Wrong password")));

		command.login(identity, "secret");

		PipelineState state = PipelineState.initial();
		advanced().getInput().applyTo(state);
		assertEquals("secret", state.get(CredentialAuthenticationAttempt.KEY).orElseThrow().getPassword());
		verify(identity).sendMessage(Component.text("Wrong password"));
		verify(identity, never()).sendMessage(Component.text(noPending()));
	}

	@DisplayName("Shows the no-pending message when the pipeline finds no journey")
	@Test
	void showsNoPendingWhenNothingIsPending() {
		when(coordinator.advance(any())).thenReturn(CompletableFuture.completedFuture(ConnectionDecision.noPending()));

		command.login(identity, "secret");

		verify(identity).sendMessage(Component.text(noPending()));
	}

	private AdvanceRequest advanced() {
		ArgumentCaptor<AdvanceRequest> request = ArgumentCaptor.forClass(AdvanceRequest.class);
		verify(coordinator).advance(request.capture());
		return request.getValue();
	}

	private String noPending() {
		return messages.getScenario().getAuthentication().getStatus().getNoPending();
	}
}
