package me.whereareiam.identica.engine.pipeline.username;

import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.AbstractGroupState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.event.identity.UsernameChangedEvent;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.UsernameStateItem;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public abstract class AbstractPersistUsernamePhase<S extends AbstractGroupState> extends AbstractUsernamePhase implements PipelinePhase<S> {
	protected final AccountPersistenceService accountPersistenceService;
	protected final EventManager eventManager;

	protected AbstractPersistUsernamePhase(
			@NotNull ProviderManager providerManager,
			@NotNull AccountUsernamePersistenceService usernamePersistenceService,
			@NotNull AccountPersistenceService accountPersistenceService,
			@NotNull EventManager eventManager
	) {
		super(providerManager, usernamePersistenceService);
		this.accountPersistenceService = accountPersistenceService;
		this.eventManager = eventManager;
	}

	@Override
	public final @NotNull CompletionStage<PhaseResult<S>> execute(@NotNull PipelineState pipelineState, @NotNull S state) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		UsernameStateItem snapshot = pipelineState.get(UsernameStateItem.KEY).orElse(null);
		ScenarioContext context = scenarioContext(pipelineState, state);
		if (snapshot == null || context == null || snapshot.getUniqueId() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		if (!hasAuthoritativeUsernameTrait(snapshot.getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String currentUsername = context.getUsername();
		String previousUsername = snapshot.getPreviousUsername();
		if (previousUsername == null || previousUsername.equals(currentUsername))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		if (isHeldByAnother(accountPersistenceService, snapshot.getUniqueId(), currentUsername)) {
			Logger.warn("Account %s keeps the username %s: its provider %s renamed it to %s, which another account holds",
					snapshot.getUniqueId(), previousUsername, snapshot.getProviderId(), currentUsername);
			return keep(context, previousUsername, state);
		}

		boolean renamed = usernamePersistenceService.rename(AccountUsernameHistoryEntry.builder()
				.uniqueId(snapshot.getUniqueId())
				.providerId(snapshot.getProviderId())
				.oldUsername(previousUsername)
				.newUsername(currentUsername)
				.source(AccountUsernameSource.PROVIDER)
				.changedAt(System.currentTimeMillis())
				.build());
		if (!renamed) return keep(context, previousUsername, state);

		eventManager.call(new UsernameChangedEvent(
				snapshot.getUniqueId(),
				previousUsername,
				currentUsername,
				snapshot.getProviderId()
		));

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Leaves the account with the username it has, which the player goes on with.
	 */
	private @NotNull CompletionStage<PhaseResult<S>> keep(@NotNull ScenarioContext context, @NotNull String username, @NotNull S state) {
		context.getIdentity().setUsername(username);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	protected abstract @Nullable ScenarioContext scenarioContext(@NotNull PipelineState pipelineState, @NotNull S state);
}
