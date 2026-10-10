package me.whereareiam.identica.engine.pipeline.scenario.session;

import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.session.phase.OpenSessionPhase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.SessionState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Open-Session Refusal")
class OpenSessionRefusalTest {
	private final EventManager eventManager = mock(EventManager.class);

	@DisplayName("A session the service refuses denies the login with the concurrent-login message")
	@Test
	void refusedSessionDeniesWithTheConcurrentLoginMessage() {
		PipelineResult result = refused(List.of("This account is already logged in.", "Log out first."));

		assertEquals(PipelineStatus.DENIED, result.getStatus());
		assertEquals("This account is already logged in.\nLog out first.", result.getMessage());
		verify(eventManager, never()).call(any(SessionOpenedEvent.class));
	}

	@DisplayName("A refused session denies the login when the message is empty")
	@Test
	void refusedSessionDeniesWithoutAMessage() {
		PipelineResult result = refused(List.of());

		assertEquals(PipelineStatus.DENIED, result.getStatus());
		verify(eventManager, never()).call(any(SessionOpenedEvent.class));
	}

	private PipelineResult refused(List<String> refusal) {
		SessionService sessionService = mock(SessionService.class);
		OpenSessionPhase phase = new OpenSessionPhase(sessionService, () -> messages(refusal), eventManager);

		UUID accountUniqueId = UUID.randomUUID();
		Session session = Session.builder()
				.uniqueId(accountUniqueId)
				.providerId("credential")
				.providerSubject("player-one")
				.originalUsername("PlayerOne")
				.build();
		SessionState state = new SessionState();
		state.setAuthContext(AuthContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.build());
		state.setSession(session);
		state.setResult(PipelineResult.complete());
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		when(sessionService.open(session)).thenReturn(CompletableFuture.completedFuture(null));

		phase.execute(pipelineState, state).toCompletableFuture().join();

		return state.getResult();
	}

	private Messages messages(List<String> refusal) {
		Messages messages = new Messages();
		Messages.Engine engine = new Messages.Engine();
		engine.setConcurrentLoginRefused(refusal);
		messages.setEngine(engine);
		return messages;
	}
}
