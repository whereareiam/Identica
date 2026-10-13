package me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Brings the username of a logged-in account up to date with its provider: the account takes the name its provider
 * gives it, a renamed account takes its own name back once nobody holds it, and a provider rename into a held name
 * goes through the username conflict rules. It runs before the provider profile is refreshed, which still holds the
 * name the provider gave the account last.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AssignAuthenticationUsernamePhase implements PipelinePhase<IdentityState> {
	private final UsernameAssigner assigner;

	@Override
	public @NotNull String id() {
		return "assign-username";
	}

	@Override
	public int order() {
		return 225;
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

		AuthContext context = pipelineState.getScenario(PipelineType.AUTHENTICATION) instanceof AuthContext authContext
				? authContext
				: null;
		Account account = state.getAccount();
		AccountProviderLink link = state.getProviderLink();
		ProviderContext provider = state.getProvider();
		if (context == null || account == null || link == null || provider == null || isBlank(provider.getProviderUsername()))
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		AccountProviderProfile profile = state.getProfile();
		UsernameAssignment assignment = assigner.assign(UsernameArrival.builder()
				.accountUniqueId(account.getUniqueId())
				.providerId(link.getProviderId())
				.username(provider.getProviderUsername().trim())
				.knownUsername(profile != null ? profile.getProviderUsername() : null)
				.firstJoin(false)
				.arrivedVia(context.getSelection().getOrigin())
				.build());
		if (assignment.isRefused()) {
			state.setResult(PipelineResult.denied(assignment.getMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		account.setUsername(assignment.getUsername());
		context.getIdentity().setUsername(assignment.getSessionUsername());
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
