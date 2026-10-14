package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.session.phase;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.identity.session.exception.ConcurrentSessionChangeException;
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
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Open Session Phase (authentication)")
class OpenSessionPhaseTest {
	private final SessionService sessions = mock(SessionService.class);
	private final EventManager events = mock(EventManager.class);
	private final OpenSessionPhase phase = new OpenSessionPhase(sessions, OpenSessionPhaseTest::messages, events);

	@DisplayName("A login that lost its session to another login of the account disconnects with engine.concurrentChangeKick")
	@Test
	void lostLoginRequiresAReconnect() {
		when(sessions.open(any(Session.class)))
				.thenReturn(CompletableFuture.failedFuture(new ConcurrentSessionChangeException(UUID.randomUUID())));
		SessionState state = completedLogin();

		phase.execute(authenticationState(), state).toCompletableFuture().join();

		assertEquals(PipelineStatus.REQUIRE_RECONNECT, state.getResult().getStatus());
		assertEquals("reconnect", state.getResult().getMessage());
		verify(events, never()).call(any(SessionOpenedEvent.class));
	}

	@DisplayName("Any other failure to open the session still fails the run")
	@Test
	void otherFailuresPropagate() {
		when(sessions.open(any(Session.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

		CompletionException failure = assertThrows(CompletionException.class,
				() -> phase.execute(authenticationState(), completedLogin()).toCompletableFuture().join());

		assertInstanceOf(IllegalStateException.class, failure.getCause());
	}

	private static SessionState completedLogin() {
		SessionState state = new SessionState();
		state.setResult(PipelineResult.complete());
		state.setAuthContext(mock(AuthContext.class));
		state.setSession(mock(Session.class));
		return state;
	}

	private static PipelineState authenticationState() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.AUTHENTICATION);
		return state;
	}

	private static Messages messages() {
		Messages.Engine engine = new Messages.Engine();
		engine.setConcurrentChangeKick(List.of("reconnect"));
		Messages messages = new Messages();
		messages.setEngine(engine);
		return messages;
	}
}
