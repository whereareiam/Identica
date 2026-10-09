package me.whereareiam.identica.feature.restriction.join.pipeline.prepare.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecisionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionType;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.model.RestrictionDecision;
import me.whereareiam.identica.feature.restriction.model.RestrictionEvaluationRequest;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApplyJoinRestrictionPhase implements PipelinePhase<PrepareGroupState> {
	private final RestrictionService restrictionService;
	private final ProviderOperations providerOperations;
	private final Provider<JoinRestrictionMessages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "apply-join-restriction";
	}

	@Override
	public int order() {
		return 50;
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
		if (state.getRequest() == null) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		ProviderContext provider = context.getProvider();
		if (provider == null || provider.getProviderId() == null || provider.getProviderId().isBlank())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		RestrictionDecision decision = restrictionService.evaluate(RestrictionEvaluationRequest.builder()
				.type(JoinRestrictionType.TYPE)
				.providerId(provider.getProviderId())
				.providerSubject(provider.getProviderSubject())
				.providerUsername(provider.getProviderUsername())
				.connectionUniqueId(state.getRequest().getIdentity().getConnectionUniqueId())
				.ip(state.getRequest().getIdentity().getIp())
				.origin(state.getRequest().getIdentity().getOrigin())
				.build());
		if (decision.isAllowed())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareAccountCandidateItem candidate = pipelineState.item(PrepareAccountCandidateItem.class).orElse(null);
		String providerName = providerOperations.displayProviderName(provider.getProviderId());
		pipelineState.putItem(PrepareDecisionItem.deny(
				Serializer.render(String.join("\n", messagesProvider.get().getDenied()), Map.of(
						"providerId", provider.getProviderId(),
						"providerName", providerName != null ? providerName : provider.getProviderId(),
						"allow", describeAllow(decision.getAllow())
				)),
				context,
				candidate != null ? candidate.getUniqueId() : null,
				state.getRequest().getIdentity().getUsername()
		), 0L);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull String describeAllow(@NotNull Set<RestrictionSignal> allow) {
		if (allow.isEmpty()) return "NONE";
		return allow.stream()
				.map(RestrictionSignal::getId)
				.collect(Collectors.joining(", "));
	}
}
