package me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Gives a moved account the name of the provider it moves to, before the provider becomes its primary link. The
 * account takes its name from that provider for the first time, so a name another account holds goes through the
 * username conflict rules as a first join, and a refusal stops the move before it changes the account's links.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AssignMigrationUsernamePhase implements PipelinePhase<IdentityState> {
	private final UsernameAssigner assigner;

	@Override
	public @NotNull String id() {
		return "assign-username";
	}

	@Override
	public int order() {
		return 350;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<IdentityState>> execute(@NotNull PipelineState pipelineState, @NotNull IdentityState state) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		MigrationContext context = state.getContext();
		Account account = state.getAccount();
		AccountProviderProfile profile = state.getProfile();
		if (context == null || account == null || profile == null || profile.getProviderUsername().isBlank())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		UsernameAssignment assignment = assigner.assign(UsernameArrival.builder()
				.accountUniqueId(account.getUniqueId())
				.providerId(profile.getProviderId())
				.username(profile.getProviderUsername().trim())
				.firstJoin(true)
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
}
