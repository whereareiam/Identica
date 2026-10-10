package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecisionItem;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Finds the account a connection belongs to before its profile is known, without reserving or changing anything,
 * and puts its preferred provider link and the journey mode of the scenario the connection heads to into the
 * prepare context for the handshake.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveKnownAccountPhase implements PipelinePhase<PrepareGroupState> {
	private final ProviderOperations providerOperations;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final AccountPersistenceService accountPersistenceService;
	private final Provider<Engine> engineProvider;

	@Override
	public @NotNull String id() {
		return "resolve-known-account";
	}

	@Override
	public int order() {
		return 400;
	}

	@Override
	public @NotNull Class<PrepareGroupState> stateType() {
		return PrepareGroupState.class;
	}

	@Override
	public boolean supports(@NotNull PipelineState pipelineState, @NotNull PrepareGroupState state) {
		return pipelineState.item(PrepareDecisionItem.class).isEmpty()
				&& state.getRequest() != null
				&& state.getRequest().getStage() == PrepareStage.HANDSHAKE;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		context.setPreferredLink(resolvePreferredLink(state.getRequest().getIdentity()));
		context.setJourneyMode(resolveJourneyMode(context.getPreferredLink()));
		pipelineState.putItem(context, 0L);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Returns the preferred link of the account linked to the subject a provider resolves for the connection, or else
	 * of the only account that uses its username.
	 */
	private @Nullable AccountProviderLink resolvePreferredLink(@NotNull ConnectionIdentity identity) {
		UUID accountUniqueId = resolveAccountUniqueId(identity);
		if (accountUniqueId == null) return null;

		return providerOperations.selectPreferredLink(providerLinkPersistenceService.findByUniqueId(accountUniqueId));
	}

	private @Nullable UUID resolveAccountUniqueId(@NotNull ConnectionIdentity identity) {
		SubjectResolution subject = providerOperations.discoverSubject(SubjectResolveContext.builder()
				.identity(identity)
				.build());
		if (subject != null && subject.getProviderSubject() != null && !subject.getProviderSubject().isBlank()) {
			AccountProviderLink link = providerLinkPersistenceService
					.findBySubject(subject.getProviderId(), subject.getProviderSubject())
					.orElse(null);
			if (link != null) return link.getUniqueId();
		}

		List<Account> accounts = accountPersistenceService.findByUsername(identity.getUsername());

		return accounts.size() == 1 ? accounts.getFirst().getUniqueId() : null;
	}

	/**
	 * Returns the journey mode of the scenario the connection heads to: authentication for a known account,
	 * registration otherwise.
	 */
	private @Nullable JourneyMode resolveJourneyMode(@Nullable AccountProviderLink preferredLink) {
		Engine engine = engineProvider.get();
		if (engine == null) return null;

		Engine.Scenario scenario = preferredLink != null
				? engine.getScenarios().getAuthentication()
				: engine.getScenarios().getRegistration();

		return scenario != null ? scenario.getJourneyMode() : null;
	}
}
