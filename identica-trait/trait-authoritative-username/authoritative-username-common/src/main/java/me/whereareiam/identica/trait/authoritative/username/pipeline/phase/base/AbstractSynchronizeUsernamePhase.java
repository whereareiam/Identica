package me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base;

import com.google.inject.Provider;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.AbstractGroupState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.pipeline.UsernameStateItem;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public abstract class AbstractSynchronizeUsernamePhase<S extends AbstractGroupState> extends AbstractUsernamePhase implements PipelinePhase<S> {
	protected final Provider<AuthoritativeUsernameMessages> messagesProvider;

	protected AbstractSynchronizeUsernamePhase(
			@NotNull ProviderManager providerManager,
			@NotNull AccountUsernameStatePersistenceService statePersistenceService,
			@NotNull Provider<AuthoritativeUsernameMessages> messagesProvider
	) {
		super(providerManager, statePersistenceService);
		this.messagesProvider = messagesProvider;
	}

	@Override
	public final @NotNull CompletionStage<PhaseResult<S>> execute(@NotNull PipelineState pipelineState, @NotNull S state) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		ScenarioContext context = scenarioContext(pipelineState, state);
		Account account = account(state);
		AccountProviderLink link = link(state);
		AccountProviderProfile profile = profile(state);
		if (context == null || account == null || link == null || profile == null) {
			state.setResult(PipelineResult.failed(missingStateMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (!hasAuthoritativeUsernameTrait(link.getProviderId()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		AccountUsernameSource source = resolveSource(account.getUniqueId());
		String previousUsername = account.getUsername();
		synchronize(account, link, profile);
		context.getIdentity().setUsername(account.getUsername());
		pipelineState.putItem(UsernameStateItem.builder()
				.uniqueId(account.getUniqueId())
				.previousUsername(previousUsername)
				.providerId(link.getProviderId())
				.providerSubject(link.getProviderSubject())
				.source(source)
				.build(), 0L);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	protected abstract @Nullable ScenarioContext scenarioContext(@NotNull PipelineState pipelineState, @NotNull S state);

	protected abstract @Nullable Account account(@NotNull S state);

	protected abstract @Nullable AccountProviderLink link(@NotNull S state);

	protected abstract @Nullable AccountProviderProfile profile(@NotNull S state);

	protected abstract @NotNull String missingStateMessage();
}
