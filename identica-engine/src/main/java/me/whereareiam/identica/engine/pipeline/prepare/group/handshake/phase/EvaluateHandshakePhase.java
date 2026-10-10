package me.whereareiam.identica.engine.pipeline.prepare.group.handshake.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.prepare.runtime.HandshakeRequestFactory;
import me.whereareiam.identica.engine.pipeline.prepare.runtime.KnownAccountResolver;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
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
public class EvaluateHandshakePhase implements PipelinePhase<PrepareGroupState> {
	private final HandshakeStore handshakeStore;
	private final KnownAccountResolver knownAccountResolver;
	private final HandshakeRequestFactory requestFactory;

	@Override
	public @NotNull String id() {
		return "evaluate-handshake";
	}

	@Override
	public int order() {
		return 100;
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
		if (state.getRequest() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		if (context.getHandshake() != null) {
			Logger.debug("Prepare reusing handshake username=%s stage=%s",
					state.getRequest().getUsername(),
					state.getRequest().getStage());
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		context.setPreferredLink(knownAccountResolver.resolvePreferredLink(state.getRequest().getIdentity()));
		HandshakeRequest request = requestFactory.create(state.getRequest().getIdentity(), context);
		HandshakeDecision decision = HandshakeDecision.allow();
		Iterable<HandshakePolicy> policies = handshakeStore.policies();
        for (HandshakePolicy policy : policies) {
			decision = merge(decision, evaluatePolicy(policy, request));
        }

		context.setHandshake(decision);
		pipelineState.putItem(context, 0L);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull HandshakeDecision evaluatePolicy(
			@NotNull HandshakePolicy policy,
			@NotNull HandshakeRequest request
	) {
		try {
			CompletionStage<HandshakeDecision> stage = policy.evaluate(request);
			if (stage == null) return HandshakeDecision.allow();

			HandshakeDecision resolved = stage.handle((decision, error) -> {
				if (error != null) {
					Logger.severe("Handshake policy failed %s", error.fillInStackTrace());
					return HandshakeDecision.allow();
				}

				return decision != null ? decision : HandshakeDecision.allow();
			}).toCompletableFuture().join();

			if (resolved.getStatus() != HandshakeDecision.Status.ALLOW)
				Logger.debug("Handshake policy %s returned %s",
						policy.getClass().getSimpleName(),
						resolved.getStatus());

			return resolved;
		} catch (Exception e) {
			Logger.severe("Handshake policy failed %s", e.fillInStackTrace());
			return HandshakeDecision.allow();
		}
	}

	private @NotNull HandshakeDecision merge(
			@NotNull HandshakeDecision current,
			@NotNull HandshakeDecision candidate
	) {
		if (current.getStatus() == HandshakeDecision.Status.DENY) return current;
		if (candidate.getStatus() == HandshakeDecision.Status.DENY) return candidate;

		return current.withAttributes(candidate.getAttributes());
	}
}
