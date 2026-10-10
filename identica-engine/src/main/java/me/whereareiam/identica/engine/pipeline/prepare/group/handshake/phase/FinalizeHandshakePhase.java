package me.whereareiam.identica.engine.pipeline.prepare.group.handshake.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.handshake.HandshakeDecisionEvent;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecisionItem;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FinalizeHandshakePhase implements PipelinePhase<PrepareGroupState> {
	private final EventManager eventManager;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "finalize-handshake";
	}

	@Override
	public int order() {
		return 200;
	}

	@Override
	public @NotNull Class<PrepareGroupState> stateType() {
		return PrepareGroupState.class;
	}

	@Override
	public boolean supports(@NotNull PipelineState pipelineState, @NotNull PrepareGroupState state) {
		return pipelineState.item(PrepareDecisionItem.class).isEmpty();
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		var request = state.getRequest();
		if (request == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		HandshakeDecision resolvedHandshake = context.getHandshake();
		HandshakeDecision decision = resolvedHandshake != null ? resolvedHandshake : HandshakeDecision.allow();
		HandshakeDecisionEvent event = new HandshakeDecisionEvent(
				new HandshakeRequest(
						request.getIdentity(),
						context.getProvider(),
						context.getPreferredLink(),
						context.getJourneyMode()
				),
				decision
		);
		eventManager.call(event);
		HandshakeDecision resolved = event.getDecision();
		if (resolved == null)
			resolved = decision;
		HandshakeDecision.Status status = resolved.getStatus();
		if (status == HandshakeDecision.Status.DENY) {
			String message = resolved.getMessage();
			if (message == null || message.isBlank()) {
				resolved = HandshakeDecision.deny(String.join("\n", messagesProvider.get()
							.getEngine()
							.getPrepare()
							.getHandshakeDenied()));
			}
			context.setHandshake(resolved);
			pipelineState.putItem(context, 0L);
			pipelineState.putItem(PrepareDecisionItem.deny(
					resolved.getMessage(),
					context,
					null,
					request.getIdentity().getUsername()
			), 0L);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		context.setHandshake(resolved);
		pipelineState.putItem(context, 0L);
		if (!request.getStage().includesProfile()) {
			pipelineState.putItem(PrepareDecisionItem.allow(
					context,
					null,
					request.getIdentity().getUsername()
			), 0L);
		}
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
