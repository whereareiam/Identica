package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.PolicyState;
import me.whereareiam.identica.identity.username.UsernameConflictHandler;
import me.whereareiam.identica.model.identity.username.UsernameConflictResult;
import me.whereareiam.identica.model.identity.username.UsernameConflictSubject;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.UsernameStateItem;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ReviewAuthenticationUsernamePhase implements PipelinePhase<PolicyState> {
	private final UsernameConflictHandler conflictHandler;
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ProviderProfilePersistenceService providerProfilePersistenceService;

	@Override
	public @NotNull String id() {
		return "review-authoritative-username";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull Class<PolicyState> stateType() {
		return PolicyState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PolicyState>> execute(@NotNull PipelineState pipelineState, @NotNull PolicyState state) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE) {
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		AuthContext context = pipelineState.getScenario(PipelineType.AUTHENTICATION) instanceof AuthContext authContext
				? authContext
				: null;
		UsernameStateItem snapshot = pipelineState.get(UsernameStateItem.KEY).orElse(null);
		if (context == null || snapshot == null || snapshot.getUniqueId() == null
				|| snapshot.getProviderId() == null || snapshot.getProviderSubject() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		Account account = accountPersistenceService.findByUniqueId(snapshot.getUniqueId())
				.map(found -> found.toBuilder().username(context.getUsername()).build())
				.orElse(Account.builder()
						.uniqueId(snapshot.getUniqueId())
						.username(context.getUsername())
						.build());
		AccountProviderLink link = providerLinkPersistenceService.findBySubject(snapshot.getProviderId(), snapshot.getProviderSubject())
				.orElseGet(() -> AccountProviderLink.builder()
						.uniqueId(snapshot.getUniqueId())
						.providerId(snapshot.getProviderId())
						.providerSubject(snapshot.getProviderSubject())
						.primaryLink(true)
						.build());
		AccountProviderProfile profile = providerProfilePersistenceService.findBySubject(snapshot.getProviderId(), snapshot.getProviderSubject())
				.orElseGet(() -> AccountProviderProfile.builder()
						.providerId(snapshot.getProviderId())
						.providerSubject(snapshot.getProviderSubject())
						.providerUsername(context.getProvider() != null
								? context.getProvider().getProviderUsername()
								: context.getUsername())
						.build());

		UsernameConflictResult conflictResult = conflictHandler.handle(
				UsernameConflictSubject.builder()
						.candidateUsername(account.getUsername())
						.account(account)
						.incomingLink(link)
						.incomingProfile(profile)
						.selection(context.getSelection())
						.build(),
				context.getUsername()
		);
		context.getIdentity().setUsername(conflictResult.getEffectiveUsername());
		if (conflictResult.isDenied()) state.setResult(PipelineResult.denied(conflictResult.getDenialMessage()));

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
