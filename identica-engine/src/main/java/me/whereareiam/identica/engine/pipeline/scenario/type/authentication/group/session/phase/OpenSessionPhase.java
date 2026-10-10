package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.session.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.IdentityMetaItem;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.SessionState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OpenSessionPhase implements PipelinePhase<SessionState> {
	private final SessionService sessionService;
	private final Provider<Messages> messagesProvider;
	private final EventManager eventManager;

	@Override
	public @NotNull String id() {
		return "open-session";
	}

	@Override
	public int order() {
		return 200;
	}

	@Override
	public @NotNull Class<SessionState> stateType() {
		return SessionState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<SessionState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull SessionState state
	) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		AuthContext authContext = state.getAuthContext();
		Session session = state.getSession();
		if (authContext == null || session == null) {
			state.setResult(PipelineResult.failed(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		PipelineType pipelineType = pipelineState.getPipelineType();
		if (pipelineType == null) {
			state.setResult(PipelineResult.failed(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}
		return sessionService.open(session)
				.thenApply(openedSession -> {
					if (openedSession == null) {
						state.setResult(PipelineResult.denied(concurrentLoginRefusedMessage()));
						return PhaseResult.pass(state);
					}

					authContext.setAccountUniqueId(openedSession.getUniqueId());
					pipelineState.setScenario(authContext);
					pipelineState.removeItem(IdentityMetaItem.class);
					publishSessionOpened(
							pipelineType,
							authContext.getConnectionUniqueId(),
							openedSession
					);
					state.setResult(result);
					return PhaseResult.pass(state);
				});
	}

	private void publishSessionOpened(
			@NotNull PipelineType pipelineType,
			UUID connectionUniqueId,
			@NotNull Session session
	) {
		if (connectionUniqueId == null) return;
		eventManager.call(new SessionOpenedEvent(
				connectionUniqueId,
				pipelineType,
				session
		));
	}

	private @NotNull String authenticationFailedMessage() {
		return joinMessage(messagesProvider.get().getScenarios().getAuthentication().getAuthenticationFailed());
	}

	/**
	 * The session service refuses a session only when the account is already online through another connection
	 * under {@code REJECT_NEW}. The denied status refuses the login; the message only explains it.
	 */
	private @NotNull String concurrentLoginRefusedMessage() {
		return joinMessage(messagesProvider.get().getEngine().getConcurrentLoginRefused());
	}

	private @NotNull String joinMessage(@NotNull List<String> lines) {
		return String.join("\n", lines);
	}

}
