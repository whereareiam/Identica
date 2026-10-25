package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity;

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
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class IdentityGroup implements PipelineGroup<IdentityState> {
	private final Provider<Messages> messagesProvider;
	@Override
	public @NotNull String id() {
		return "identity";
	}

	@Override
	public int order() {
		return 500;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	public @NotNull IdentityState initializeState(
			@NotNull PipelineState pipelineState,
			@Nullable PipelineResult currentResult
	) {
		IdentityState state = new IdentityState();
		if (currentResult != null) state.setResult(currentResult);

		return state;
	}

	@Override
	public @NotNull GroupOutcome complete(@NotNull PipelineState pipelineState, @NotNull IdentityState state) {
		PipelineResult result = state.getResult();
		if (result == null)
			return GroupOutcome.result(PipelineResult.failed(identityGroupMissingResultMessage()));
		return GroupOutcome.result(result);
	}

	private @NotNull PlayerMessage identityGroupMissingResultMessage() {
		return ScenarioMessages.of(PipelineType.AUTHENTICATION).getIdentityMissingResult().create(messagesProvider.get());
	}
}
