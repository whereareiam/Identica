package me.whereareiam.identica.engine.pipeline.scenario.session;

import me.whereareiam.identica.engine.pipeline.scenario.authentication.group.session.phase.OpenSessionPhase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Open-Session Completion Pending")
class OpenSessionCompletionPendingTest {
	@DisplayName("Opening an authentication session emits a pending-completion event for the new session")
	@Test
	void authenticationOpenSessionStoresPendingCompletionInvocation() {
		SessionService sessionService = mock(SessionService.class);
		EventManager eventManager = mock(EventManager.class);
		OpenSessionPhase phase = new OpenSessionPhase(
				sessionService,
				this::messages,
				this::settings,
				eventManager
		);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID identicaUniqueId = UUID.randomUUID();
		AuthContext context = AuthContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(identicaUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("lobby")
				.build();
		Session session = Session.builder()
				.uniqueId(identicaUniqueId)
				.providerId("password")
				.providerSubject("player-one")
				.originalUsername("PlayerOne")
				.effectiveUsername("PlayerOne")
				.build();
		me.whereareiam.identica.engine.pipeline.scenario.authentication.group.session.SessionState state =
				new me.whereareiam.identica.engine.pipeline.scenario.authentication.group.session.SessionState();
		state.setAuthContext(context);
		state.setSession(session);
		state.setResult(PipelineResult.complete());
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);

		when(sessionService.open(session, SessionConcurrencyPolicy.REPLACE_EXISTING))
				.thenReturn(CompletableFuture.completedFuture(session));
		when(sessionService.findByUniqueId(identicaUniqueId))
				.thenReturn(CompletableFuture.completedFuture(java.util.Optional.empty()));

		phase.execute(pipelineState, state).toCompletableFuture().join();

		verify(eventManager).call(argThat(event -> event instanceof SessionOpenedEvent requested
				&& requested.getConnectionUniqueId().equals(connectionUniqueId)
				&& requested.getPipelineType() == PipelineType.AUTHENTICATION
				&& identicaUniqueId.equals(requested.getSession().getUniqueId())
				&& "password".equals(requested.getSession().getProviderId())
				&& !requested.isSessionReused()
		));
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Connection connection = new Messages.Connection();
		Messages.Connection.Authentication authentication = new Messages.Connection.Authentication();
		authentication.setAuthenticationFailed(List.of("failed"));
		connection.setAuthentication(authentication);
		messages.setConnection(connection);
		return messages;
	}

	private Settings settings() {
		Settings settings = new Settings();
		Settings.Connection connection = new Settings.Connection();
		Settings.AuthenticationScenario authentication = new Settings.AuthenticationScenario();
		authentication.setSessionConcurrencyPolicy(SessionConcurrencyPolicy.REPLACE_EXISTING);
		connection.setAuthentication(authentication);
		settings.setConnection(connection);
		return settings;
	}
}
