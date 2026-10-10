package me.whereareiam.identica.engine.pipeline.completion.group.context.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.completion.CompletionPipelineState;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveCompletionSessionPhase implements PipelinePhase<CompletionPipelineState> {
	private final SessionService sessionService;

	@Override
	public @NotNull String id() {
		return "resolve-completion-session";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull Class<CompletionPipelineState> stateType() {
		return CompletionPipelineState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<CompletionPipelineState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull CompletionPipelineState state
	) {
		UUID accountUniqueId = state.getPendingState().getAccountUniqueId();
		if (accountUniqueId == null) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		UUID connectionUniqueId = state.getPendingState().getConnectionUniqueId();
		Session session = connectionUniqueId != null
				? sessionService.findByConnection(accountUniqueId, SessionConnection.of(connectionUniqueId)).join().orElse(null)
				: null;
		// A completion without a connection of its own, or for a session opened without one, uses the
		// account's newest session.
		if (session == null)
			session = sessionService.findByUniqueId(accountUniqueId).join().orElse(null);
		state.setSession(session);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
