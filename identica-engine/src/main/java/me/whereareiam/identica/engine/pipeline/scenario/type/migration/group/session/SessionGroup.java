package me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.GroupOutcome;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.pipeline.PipelineGroup;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.SessionState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionGroup implements PipelineGroup<SessionState> {
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "session";
	}

	@Override
	public int order() {
		return 700;
	}

	@Override
	public @NotNull Class<SessionState> stateType() {
		return SessionState.class;
	}

	@Override
	public @NotNull SessionState initializeState(
			@NotNull PipelineState pipelineState,
			@Nullable PipelineResult currentResult
	) {
		SessionState state = new SessionState();
		if (currentResult != null)
			state.setResult(currentResult);
		return state;
	}

	@Override
	public @NotNull GroupOutcome complete(@NotNull PipelineState pipelineState, @NotNull SessionState state) {
		PipelineResult result = state.getResult();
		if (result == null)
			return GroupOutcome.result(PipelineResult.failed(sessionGroupMissingResultMessage()));
		return GroupOutcome.result(result);
	}

	private @NotNull PlayerMessage sessionGroupMissingResultMessage() {
		return ScenarioMessages.of(PipelineType.MIGRATION).getSessionMissingResult().create(messagesProvider.get());
	}
}
