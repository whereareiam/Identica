package me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.IdentityState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.event.identity.UsernameChangedEvent;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.engine.pipeline.username.AbstractUsernamePhase;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class SynchronizeMigrationUsernamePhase extends AbstractUsernamePhase implements PipelinePhase<IdentityState> {
	private final AccountPersistenceService accountPersistenceService;
	private final EventManager eventManager;
	private final Provider<UsernameMessages> messagesProvider;

	@Inject
	public SynchronizeMigrationUsernamePhase(
			ProviderManager providerManager,
			AccountUsernamePersistenceService usernamePersistenceService,
			AccountPersistenceService accountPersistenceService,
			EventManager eventManager,
			Provider<UsernameMessages> messagesProvider
	) {
		super(providerManager, usernamePersistenceService);
		this.accountPersistenceService = accountPersistenceService;
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
		if (changed && !applyUsernameChange(state, previousUsername)) state.getAccount().setUsername(previousUsername);
		state.getContext().getIdentity().setUsername(state.getAccount().getUsername());
		saveSource(state.getAccount().getUniqueId(), AccountUsernameSource.PROVIDER);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Renames the account to the username its provider reports, unless another account holds that username.
	 *
	 * @return whether the account was renamed
	 */
	private boolean applyUsernameChange(@NotNull IdentityState state, @NotNull String previousUsername) {
		Account account = state.getAccount();
		String providerId = state.getLink().getProviderId();
		if (isHeldByAnother(accountPersistenceService, account.getUniqueId(), account.getUsername())) {
			Logger.warn("Account %s keeps the username %s: its provider %s renamed it to %s, which another account holds",
					account.getUniqueId(), previousUsername, providerId, account.getUsername());
			return false;
		}

		boolean renamed = usernamePersistenceService.rename(AccountUsernameHistoryEntry.builder()
				.uniqueId(account.getUniqueId())
				.providerId(providerId)
				.oldUsername(previousUsername)
				.newUsername(account.getUsername())
				.source(AccountUsernameSource.PROVIDER)
				.changedAt(System.currentTimeMillis())
				.build());
		if (!renamed) return false;

		eventManager.call(new UsernameChangedEvent(account.getUniqueId(), previousUsername, account.getUsername(), providerId));
		return true;
	}
}
