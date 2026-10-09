package me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.migration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.IdentityState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.event.UsernameChangedEvent;
import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameHistoryEntry;
import me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base.AbstractUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class SynchronizeMigrationUsernamePhase extends AbstractUsernamePhase implements PipelinePhase<IdentityState> {
	private final AccountPersistenceService accountPersistenceService;
	private final AccountUsernameHistoryPersistenceService historyPersistenceService;
	private final EventManager eventManager;
	private final Provider<AuthoritativeUsernameMessages> messagesProvider;

	@Inject
	public SynchronizeMigrationUsernamePhase(
			ProviderManager providerManager,
			AccountUsernameStatePersistenceService statePersistenceService,
			AccountPersistenceService accountPersistenceService,
			AccountUsernameHistoryPersistenceService historyPersistenceService,
			EventManager eventManager,
			Provider<AuthoritativeUsernameMessages> messagesProvider
	) {
		super(providerManager, statePersistenceService);
		this.accountPersistenceService = accountPersistenceService;
		this.historyPersistenceService = historyPersistenceService;
		this.eventManager = eventManager;
		this.messagesProvider = messagesProvider;
	}

	@Override
	public @NotNull String id() {
		return "authoritative-username-sync";
	}

	@Override
	public int order() {
		return 500;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<IdentityState>> execute(@NotNull PipelineState pipelineState, @NotNull IdentityState state) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE) {
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (state.getContext() == null || state.getAccount() == null || state.getLink() == null || state.getProfile() == null) {
			state.setResult(PipelineResult.failed(String.join("\n", messagesProvider.get().getPipeline().getIdentity().getSynchronizationFailed())));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (!hasAuthoritativeUsernameTrait(state.getLink().getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String previousUsername = state.getAccount().getUsername();
		boolean changed = synchronize(state.getAccount(), state.getLink(), state.getProfile());
		state.getContext().getIdentity().setUsername(state.getAccount().getUsername());
		saveSource(state.getAccount().getUniqueId(), AccountUsernameSource.PROVIDER);

		if (changed) applyUsernameChange(state, previousUsername);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private void applyUsernameChange(@NotNull IdentityState state, @NotNull String previousUsername) {
		accountPersistenceService.updateUsername(state.getAccount().getUniqueId(), state.getAccount().getUsername());
		historyPersistenceService.record(AccountUsernameHistoryEntry.builder()
				.uniqueId(state.getAccount().getUniqueId())
				.providerId(state.getLink().getProviderId())
				.oldUsername(previousUsername)
				.newUsername(state.getAccount().getUsername())
				.source(AccountUsernameSource.PROVIDER)
				.changedAt(System.currentTimeMillis())
				.build());
		eventManager.call(new UsernameChangedEvent(
				state.getAccount().getUniqueId(),
				previousUsername,
				state.getAccount().getUsername(),
				state.getLink().getProviderId()
		));
	}
}
