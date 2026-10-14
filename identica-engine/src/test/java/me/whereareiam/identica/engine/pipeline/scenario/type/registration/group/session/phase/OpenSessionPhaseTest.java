package me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.session.phase;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.identity.session.exception.ConcurrentSessionChangeException;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.SessionState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Open Session Phase (registration)")
class OpenSessionPhaseTest {
	private final SessionService sessions = mock(SessionService.class);
	private final EventManager events = mock(EventManager.class);
	private final OpenSessionPhase phase = new OpenSessionPhase(sessions, OpenSessionPhaseTest::messages, events);

	@DisplayName("A registration that lost its session to another login of the account disconnects with engine.concurrentChangeKick")
	@Test
	void lostRegistrationRequiresAReconnect() {
		when(sessions.open(any(Session.class)))
				.thenReturn(CompletableFuture.failedFuture(new ConcurrentSessionChangeException(UUID.randomUUID())));
		SessionState state = new SessionState();
		state.setResult(PipelineResult.complete());
		state.setContext(mock(RegistrationContext.class));
		state.setSession(mock(Session.class));
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.REGISTRATION);

		phase.execute(pipelineState, state).toCompletableFuture().join();

		assertEquals(PipelineStatus.REQUIRE_RECONNECT, state.getResult().getStatus());
		assertEquals("reconnect", state.getResult().getMessage());
		verify(events, never()).call(any(SessionOpenedEvent.class));
	}

	private static Messages messages() {
		Messages.Engine engine = new Messages.Engine();
		engine.setConcurrentChangeKick(List.of("reconnect"));
		Messages messages = new Messages();
		messages.setEngine(engine);
		return messages;
	}
}
