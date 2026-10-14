package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Asks every claim source which provider should handle the connection and ranks their claims into its selection.
 *
 * <p>It runs in both preparation stages. The profile stage starts from the selection of the handshake and adds
 * what only the profile shows, such as a subject a provider verified.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CollectProviderClaimsPhase implements PipelinePhase<PrepareGroupState> {
	private final Registry<ProviderClaimSource> sources;
	private final ProviderSelector providerSelector;
	private final ProviderOperations providerOperations;

	@Override
	public @NotNull String id() {
		return "collect-provider-claims";
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
		return state.getRequest() != null;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		PrepareRequest request = state.getRequest();
		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		ConnectionIdentity identity = request.getIdentity();
		ProviderClaimRequest claimRequest = ProviderClaimRequest.builder()
				.identity(identity)
				.stage(request.getStage())
				.subjects(resolveSubjects(identity))
				.selection(context.getSelection())
				.build();

		ProviderSelection selection = providerSelector.claim(context.getSelection(), collect(claimRequest));
		context.setSelection(selection);
		pipelineState.put(PrepareContextItem.KEY, context);
		Logger.debug("Prepare selected provider stage=%s username=%s provider=%s origin=%s beaten=%s",
				request.getStage(),
				identity.getUsername(),
				selection.getWinnerProviderId(),
				selection.getOrigin(),
				selection.getBeaten());

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull List<SubjectResolution> resolveSubjects(@NotNull ConnectionIdentity identity) {
		if (identity.getUsername().isBlank()) return List.of();

		return providerOperations.resolveSubjects(SubjectResolveContext.builder()
				.identity(identity)
				.build());
	}

	private @NotNull List<ProviderClaim> collect(@NotNull ProviderClaimRequest request) {
		List<ProviderClaim> claims = new ArrayList<>();
		for (ProviderClaimSource source : sources.values())
			claims.addAll(claimsOf(source, request));

		return claims;
	}

	/**
	 * A failing source only loses its own claims; the connection is still prepared with the others.
	 */
	private @NotNull Collection<ProviderClaim> claimsOf(
			@NotNull ProviderClaimSource source,
			@NotNull ProviderClaimRequest request
	) {
		try {
			return source.claims(request);
		} catch (RuntimeException e) {
			Logger.severe(e, "Provider claim source %s failed", source.getClass().getSimpleName());
			return List.of();
		}
	}
}
