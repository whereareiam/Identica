package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.prepare.runtime.ConnectionProviderContextResolver;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveProfilePhase implements PipelinePhase<PrepareGroupState> {
	private final ProviderOperations providerOperations;
	private final ConnectionProviderContextResolver providerContextResolver;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final AccountPersistenceService accountPersistenceService;

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
		return pipelineState.get(PrepareDecision.KEY).isEmpty()
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

		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		String requestedUsername = request.getIdentity().getUsername();
		if (requestedUsername.isBlank()) {
			Logger.warn("Prepare profile missing username key=%s", request.getConnectionKey());
			pipelineState.put(PrepareDecision.KEY, PrepareDecision.allow(
					context,
					null,
					""
			));

			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		SubjectResolveContext subjectContext = SubjectResolveContext.builder()
				.identity(request.getIdentity())
				.build();
		SubjectResolution discovered = providerOperations.discoverSubject(subjectContext);
		var resolution = isChosenElsewhere(context.getProvider())
				? discovered
				: selectEntrypointSubject(request.getIdentity(), subjectContext, discovered);
		if (resolution == null) {
			Logger.debug("Prepare profile deferred username=%s stage=%s",
					requestedUsername,
					request.getStage());
			pipelineState.put(PrepareDecision.KEY, PrepareDecision.allow(
					context,
					null,
					requestedUsername
			));
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
		pipelineState.put(PrepareContextItem.KEY, context);
		Logger.debug("Prepare resolved provider username=%s provider=%s subject=%s verified=%s",
				requestedUsername,
				resolution.getProviderId(),
				resolution.getProviderSubject(),
				resolution.isVerified());

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Returns the subject of the provider whose hostname the player joined through, when the player is entitled to
	 * it: the player's own account uses that provider, or the player has no account yet. Otherwise the discovered
	 * subject stands, so a hostname never moves an existing account to a provider it does not use. A provider that
	 * protects a name has already refused the connection during the handshake, before any subject is resolved.
	 *
	 * @param identity identity of the connection
	 * @param subjectContext context the providers resolve subjects from
	 * @param discovered subject of the highest-ranked provider that resolves one, or null
	 * @return subject the connection continues with, or null when no provider resolves one
	 */
	private @Nullable SubjectResolution selectEntrypointSubject(
			@NotNull ConnectionIdentity identity,
			@NotNull SubjectResolveContext subjectContext,
			@Nullable SubjectResolution discovered
	) {
		ConnectionIdentity.Origin origin = identity.getOrigin();
		if (origin == null || origin.getHost().isBlank()) return discovered;

		ResolvedEntrypoint entrypoint = providerOperations.resolveEntrypoint(
				origin.getHost(),
				origin.getPort() != null ? origin.getPort() : -1
		);
		if (entrypoint == null) return discovered;

		String providerId = entrypoint.getProviderId();
		if (discovered != null && providerId.equalsIgnoreCase(discovered.getProviderId())) return discovered;

		SubjectResolution selected = providerOperations.resolveSelectedSubject(providerId, subjectContext);
		if (selected == null) return discovered;
		if (providerLinkPersistenceService.findBySubject(selected.getProviderId(), selected.getProviderSubject()).isPresent())
			return selected;
		if (hasAccount(identity, discovered)) return discovered;

		return selected;
	}

	/**
	 * Returns whether the connection's provider was chosen by something other than its hostname, such as a pending
	 * migration, which a hostname does not override.
	 */
	private boolean isChosenElsewhere(@Nullable ProviderContext provider) {
		return provider != null && provider.getSource() != null && provider.getSource() != ProviderOrigin.ENTRYPOINT;
	}

	private boolean hasAccount(@NotNull ConnectionIdentity identity, @Nullable SubjectResolution discovered) {
		if (discovered != null && providerLinkPersistenceService
				.findBySubject(discovered.getProviderId(), discovered.getProviderSubject())
				.isPresent())
			return true;

		return !accountPersistenceService.findByUsername(identity.getUsername()).isEmpty();
	}
}
