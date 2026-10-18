package me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.address.AccountAddressService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Records the address an account was registered from as soon as the account exists, so the next registration from
 * that address already counts it, even when a later phase refuses this one.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RecordRegistrationAddressPhase implements PipelinePhase<IdentityState> {
	private final AccountAddressService addresses;

	@Override
	public @NotNull String id() {
		return "record-registration-address";
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
	public @NotNull CompletionStage<PhaseResult<IdentityState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull IdentityState state
	) {
		PipelineResult result = state.getResult();
		Account account = state.getAccount();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE || account == null || state.getContext() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		addresses.recordRegistration(account.getUniqueId(), state.getContext().getIp());
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}
}
