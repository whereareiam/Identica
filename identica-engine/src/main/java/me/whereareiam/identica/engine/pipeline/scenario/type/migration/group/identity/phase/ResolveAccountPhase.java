package me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveAccountPhase implements PipelinePhase<IdentityState> {
	private final AccountPersistenceService accountPersistenceService;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "resolve-account";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<IdentityState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull IdentityState state
	) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		MigrationContext context = resolveContext(pipelineState);
		if (context == null) {
			state.setResult(PipelineResult.failed(accountMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		UUID accountUniqueId = context.getAccountUniqueId();
		if (accountUniqueId == null) {
			state.setResult(PipelineResult.failed(accountMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		Account account = accountPersistenceService.findByUniqueId(accountUniqueId).orElse(null);
		if (account == null) {
			state.setResult(PipelineResult.failed(accountMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		accountPersistenceService.updateLastSeen(accountUniqueId, System.currentTimeMillis());
		context.setAccountUniqueId(accountUniqueId);
		pipelineState.setScenario(context);
		state.setContext(context);
		state.setAccount(account);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull PlayerMessage accountMissingMessage() {
		return ScenarioMessages.of(PipelineType.MIGRATION).getAccountMissing().create(messagesProvider.get());
	}


	private MigrationContext resolveContext(@NotNull PipelineState pipelineState) {
		PipelineType pipelineType = pipelineState.getPipelineType();
		if (pipelineType == null)
			return null;
		return pipelineState.getScenario(pipelineType) instanceof MigrationContext migrationContext
				? migrationContext
				: null;
	}
}
