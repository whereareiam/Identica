package me.whereareiam.identica.engine.pipeline.prepare.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Decides the username a joining player plays under before the proxy names the player: the account's username, or
 * a temporary one while the holder of the account's new username keeps playing. A player the username conflict
 * rules refuse is refused here, before any prompt, when the provider is settled. A provider only guessed from the
 * username or from a subject it resolves may not be the one the player chooses, so such a refusal is left to the
 * scenario, which checks the username again once the provider is chosen. Nothing is written yet; the scenario that
 * follows writes the usernames.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApplyUsernamePhase implements PipelinePhase<PrepareGroupState> {
	private static final Set<ProviderClaimOrigin> GUESSED_ORIGINS = EnumSet.of(
			ProviderClaimOrigin.NAME_MATCH,
			ProviderClaimOrigin.DISCOVERY
	);

	private final UsernameAssigner assigner;
	private final ProviderLinkPersistenceService links;

	@Override
	public @NotNull String id() {
		return "apply-username";
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
		if (pipelineState.has(PrepareDecision.KEY) || state.getRequest() == null) return false;

		ProviderContext provider = pipelineState.get(PrepareContextItem.KEY).map(PrepareContextItem::getProvider).orElse(null);
		PrepareAccountCandidateItem candidate = pipelineState.get(PrepareAccountCandidateItem.KEY).orElse(null);
		return provider != null
				&& provider.getProviderId() != null
				&& !provider.getProviderId().isBlank()
				&& candidate != null
				&& candidate.getAccount() != null;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		PrepareAccountCandidateItem candidate = pipelineState.get(PrepareAccountCandidateItem.KEY).orElse(null);
		ProviderContext provider = context.getProvider();
		if (candidate == null || candidate.getAccount() == null || provider == null || provider.getProviderId() == null
				|| state.getRequest() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String username = state.getRequest().getIdentity().getUsername();
		UsernameAssignment assignment = assigner.preview(UsernameArrival.builder()
				.accountUniqueId(candidate.getUniqueId())
				.providerId(provider.getProviderId())
				.username(username)
				.knownUsername(knownUsername(candidate))
				.firstJoin(isFirstJoin(candidate, provider))
				.arrivedVia(context.getSelection().getOrigin())
				.build());
		if (assignment.isRefused()) {
			refuse(pipelineState, context, candidate, username, assignment);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		Account account = candidate.getAccount();
		account.setUsername(assignment.getUsername());
		candidate.setEffectiveUsername(assignment.getSessionUsername());
		pipelineState.put(PrepareAccountCandidateItem.KEY, candidate);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Refuses the player before any prompt, unless the provider is only guessed: then the player may still choose
	 * another provider, and the scenario checks the username again once one is chosen.
	 */
	private static void refuse(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareContextItem context,
			@NotNull PrepareAccountCandidateItem candidate,
			@NotNull String username,
			@NotNull UsernameAssignment assignment
	) {
		ProviderClaimOrigin origin = context.getSelection().getOrigin();
		if (isGuessed(origin)) {
			Logger.debug("Prepare left a username refusal to the scenario username=%s origin=%s", username, origin);
			return;
		}

		pipelineState.put(PrepareDecision.KEY, PrepareDecision.deny(
				assignment.getMessage(),
				context,
				candidate.getUniqueId(),
				username
		));
	}

	private static boolean isGuessed(@Nullable ProviderClaimOrigin origin) {
		return origin != null && GUESSED_ORIGINS.contains(origin);
	}

	private static @Nullable String knownUsername(@NotNull PrepareAccountCandidateItem candidate) {
		AccountProviderProfile profile = candidate.getProfile();
		return profile != null ? profile.getProviderUsername() : null;
	}

	/**
	 * A player joins through the provider for the first time when its subject is linked to no account yet, which
	 * includes a provider the account is moving to.
	 */
	private boolean isFirstJoin(@NotNull PrepareAccountCandidateItem candidate, @NotNull ProviderContext provider) {
		String subject = provider.getProviderSubject();
		if (subject == null || subject.isBlank()) return candidate.getLink() == null;

		return links.findBySubject(provider.getProviderId(), subject)
				.map(AccountProviderLink::getUniqueId)
				.filter(linked -> linked.equals(candidate.getUniqueId()))
				.isEmpty();
	}
}
