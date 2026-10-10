package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.prepare.runtime.ConnectionProviderContextResolver;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecisionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveProfilePhase implements PipelinePhase<PrepareGroupState> {
	private final ProviderOperations providerOperations;
	private final ConnectionProviderContextResolver providerContextResolver;

	@Override
	public @NotNull String id() {
		return "resolve-profile";
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
		return pipelineState.item(PrepareDecisionItem.class).isEmpty()
				&& state.getRequest() != null
				&& state.getRequest().getStage().includesProfile();
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		var request = state.getRequest();
		if (request == null) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		String requestedUsername = request.getIdentity().getUsername();
		if (requestedUsername.isBlank()) {
			Logger.warn("Prepare profile missing username key=%s", request.getConnectionKey());
			pipelineState.putItem(PrepareDecisionItem.allow(
					context,
					null,
					""
			), 0L);

			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		var resolution = providerOperations.discoverSubject(SubjectResolveContext.builder()
				.identity(request.getIdentity())
				.build());
		if (resolution == null) {
			Logger.debug("Prepare profile deferred username=%s stage=%s",
					requestedUsername,
					request.getStage());
			pipelineState.putItem(PrepareDecisionItem.allow(
					context,
					null,
					requestedUsername
			), 0L);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ProviderContext provider = ProviderContext.of(
				resolution.getProviderId(),
				resolution.getProviderSubject(),
				requestedUsername,
				providerContextResolver.resolveSource(request.getIdentity(), resolution.getProviderId())
		);
		if (provider != null) provider.setSubjectVerified(resolution.isVerified());
		context.setProvider(provider);
		pipelineState.putItem(context, 0L);
		Logger.debug("Prepare resolved provider username=%s provider=%s subject=%s verified=%s",
				requestedUsername,
				resolution.getProviderId(),
				resolution.getProviderSubject(),
				resolution.isVerified());

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
