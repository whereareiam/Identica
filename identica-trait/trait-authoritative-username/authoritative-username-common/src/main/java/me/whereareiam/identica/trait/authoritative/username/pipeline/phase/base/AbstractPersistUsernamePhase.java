package me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.AbstractGroupState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.event.UsernameChangedEvent;
import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameHistoryEntry;
import me.whereareiam.identica.trait.authoritative.username.pipeline.UsernameStateItem;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public abstract class AbstractPersistUsernamePhase<S extends AbstractGroupState> extends AbstractUsernamePhase implements PipelinePhase<S> {
	protected final AccountPersistenceService accountPersistenceService;
	protected final AccountUsernameHistoryPersistenceService historyPersistenceService;
	protected final EventManager eventManager;

	protected AbstractPersistUsernamePhase(
			@NotNull ProviderManager providerManager,
			@NotNull AccountUsernameStatePersistenceService statePersistenceService,
			@NotNull AccountPersistenceService accountPersistenceService,
			@NotNull AccountUsernameHistoryPersistenceService historyPersistenceService,
			@NotNull EventManager eventManager
	) {
		super(providerManager, statePersistenceService);
		this.accountPersistenceService = accountPersistenceService;
		this.historyPersistenceService = historyPersistenceService;
		this.eventManager = eventManager;
	}

	@Override
	public final @NotNull CompletionStage<PhaseResult<S>> execute(@NotNull PipelineState pipelineState, @NotNull S state) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		UsernameStateItem snapshot = pipelineState.item(UsernameStateItem.class).orElse(null);
		ScenarioContext context = scenarioContext(pipelineState, state);
		if (snapshot == null || context == null || snapshot.getUniqueId() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		if (!hasAuthoritativeUsernameTrait(snapshot.getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String currentUsername = context.getUsername();
		saveSource(snapshot.getUniqueId(), Objects.equals(snapshot.getPreviousUsername(), currentUsername)
				? snapshot.getSource()
				: AccountUsernameSource.PROVIDER);

		if (!Objects.equals(snapshot.getPreviousUsername(), currentUsername) && snapshot.getPreviousUsername() != null) {
			accountPersistenceService.updateUsername(snapshot.getUniqueId(), currentUsername);
			historyPersistenceService.record(AccountUsernameHistoryEntry.builder()
					.uniqueId(snapshot.getUniqueId())
					.providerId(snapshot.getProviderId())
					.oldUsername(snapshot.getPreviousUsername())
					.newUsername(currentUsername)
					.source(AccountUsernameSource.PROVIDER)
					.changedAt(System.currentTimeMillis())
					.build());
			eventManager.call(new UsernameChangedEvent(
					snapshot.getUniqueId(),
					snapshot.getPreviousUsername(),
					currentUsername,
					snapshot.getProviderId()
			));
		}

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	protected abstract @Nullable ScenarioContext scenarioContext(@NotNull PipelineState pipelineState, @NotNull S state);
}
