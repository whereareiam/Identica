package me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.database.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.PolicyState;
import me.whereareiam.identica.trait.authoritative.username.conflict.resolver.UsernameConflictResolver;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictResult;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictSubject;
import me.whereareiam.identica.trait.authoritative.username.pipeline.UsernameStateItem;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ReviewAuthenticationUsernamePhase implements PipelinePhase<PolicyState> {
	private final UsernameConflictResolver conflictResolver;
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
		UsernameStateItem snapshot = pipelineState.item(UsernameStateItem.class).orElse(null);
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

		UsernameConflictResult conflictResult = conflictResolver.handle(
				UsernameConflictSubject.builder()
						.candidateUsername(account.getUsername())
						.account(account)
						.incomingLink(link)
						.incomingProfile(profile)
						.providerContext(context.getProvider())
						.build(),
				context.getUsername()
		);
		context.getIdentity().setUsername(conflictResult.getEffectiveUsername());
		if (conflictResult.isDenied()) state.setResult(PipelineResult.denied(conflictResult.getDenialMessage()));

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
