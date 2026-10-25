package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolveIdentityPhase implements PipelinePhase<IdentityState> {
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "resolve-identity";
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

		PipelineState source = result.getState() != null ? result.getState() : pipelineState;
		AuthContext authContext = resolveAuthContext(source);
		if (authContext == null) {
			state.setResult(PipelineResult.failed(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ProviderContext provider = authContext.getProvider();
		if (provider == null
				|| isBlank(provider.getProviderId())
				|| isBlank(provider.getProviderSubject())
				|| isBlank(provider.getProviderUsername())) {
			state.setResult(PipelineResult.denied(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		Optional<AccountProviderLink> linkOptional = providerLinkPersistenceService
				.findBySubject(provider.getProviderId(), provider.getProviderSubject());
		if (linkOptional.isEmpty()) {
			state.setResult(PipelineResult.denied(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		AccountProviderLink link = linkOptional.get();
		UUID uniqueId = link.getUniqueId();
		UUID contextId = authContext.getAccountUniqueId();
		if (contextId != null && !contextId.equals(uniqueId)) {
			state.setResult(PipelineResult.denied(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		Account account = accountPersistenceService.findByUniqueId(uniqueId).orElse(null);
		if (account == null) {
			state.setResult(PipelineResult.denied(authenticationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		accountPersistenceService.updateLastSeen(uniqueId, System.currentTimeMillis());
		authContext.setAccountUniqueId(uniqueId);
		pipelineState.setScenario(authContext);
		state.setProvider(provider);
		state.setProviderLink(link);
		state.setAccount(account);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull PlayerMessage authenticationFailedMessage() {
		return ScenarioMessages.of(PipelineType.AUTHENTICATION).getFailed().create(messagesProvider.get());
	}

	private boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

	private @Nullable AuthContext resolveAuthContext(@NotNull PipelineState source) {
		PipelineType pipelineType = source.getPipelineType();
		if (pipelineType == null)
			return null;
		return source.getScenario(pipelineType) instanceof AuthContext authContext ? authContext : null;
	}
}
