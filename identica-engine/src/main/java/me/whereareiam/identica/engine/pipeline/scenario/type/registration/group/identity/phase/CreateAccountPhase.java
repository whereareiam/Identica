package me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CreateAccountPhase implements PipelinePhase<IdentityState> {
	private final AccountPersistenceService accountPersistenceService;
	private final AccountReservationPersistenceService accountReservationPersistenceService;
	private final Provider<Messages> messagesProvider;
	private static final int CREATE_ATTEMPTS = 3;

	private final UniqueIdGenerator uniqueIdGenerator;
	private final UsernameAssigner usernameAssigner;
	private final Provider<UsernameMessages> usernameMessagesProvider;

	@Override
	public @NotNull String id() {
		return "create-account";
	}

	@Override
	public int order() {
		return 300;
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

		RegistrationContext context = state.getContext();
		AccountProviderProfile profile = state.getProfile();
		if (context == null || profile == null) {
			state.setResult(PipelineResult.failed(accountCreationMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		UUID uniqueId = context.getAccountUniqueId();
		if (uniqueId == null || uniqueIdGenerator.requiresConfiguredUniqueId())
			uniqueId = resolveNewAccountId(context, profile);

		if (uniqueId == null) {
			state.setResult(PipelineResult.failed(accountCreationMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (accountPersistenceService.findByUniqueId(uniqueId).isPresent()) {
			state.setResult(PipelineResult.denied(accountAlreadyExistsMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		long now = System.currentTimeMillis();
		Creation creation = create(context, profile, uniqueId, now);
		if (creation.account() == null) {
			state.setResult(PipelineResult.denied(creation.refusal()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		deleteReservation(profile.getProviderUsername());

		context.setAccountUniqueId(uniqueId);
		pipelineState.setScenario(context);

		state.setAccount(creation.account());
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Creates the account under the username the conflict rules assign it. Another account that takes the username
	 * between the decision and the creation makes the rules decide again.
	 */
	private @NotNull Creation create(
			@NotNull RegistrationContext context,
			@NotNull AccountProviderProfile profile,
			@NotNull UUID uniqueId,
			long now
	) {
		UsernameArrival arrival = UsernameArrival.builder()
				.accountUniqueId(uniqueId)
				.providerId(profile.getProviderId())
				.username(profile.getProviderUsername())
				.firstJoin(true)
				.arrivedVia(context.getSelection().getOrigin())
				.build();
		for (int attempt = 0; attempt < CREATE_ATTEMPTS; attempt++) {
			UsernameAssignment assignment = usernameAssigner.assign(arrival);
			if (assignment.isRefused()) return new Creation(null, assignment.getMessage());

			Account account = Account.builder()
					.uniqueId(uniqueId)
					.username(assignment.getUsername())
					.createdAt(now)
					.lastSeenAt(now)
					.build();
			UsernameWriteResult result = accountPersistenceService.create(account, assignment.getSource(), profile.getProviderId());
			if (!result.isWritten()) continue;

			context.getIdentity().setUsername(assignment.getSessionUsername());
			return new Creation(account, null);
		}

		Logger.warn("Account %s could not be created: other accounts kept taking the usernames it was given", uniqueId);
		return new Creation(null, takenMessage(profile.getProviderUsername()));
	}

	private @NotNull String takenMessage(@NotNull String username) {
		String lines = String.join("\n", usernameMessagesProvider.get().getPipeline().getPolicy().getTaken());

		return Serializer.render(lines, Map.of("username", username));
	}

	private @NotNull String accountAlreadyExistsMessage() {
		return String.join("\n", messagesProvider.get().getScenarios().getRegistration().getAccountAlreadyExists());
	}

	private @NotNull String accountCreationMissingMessage() {
		return String.join("\n", messagesProvider.get()
				.getScenarios()
				.getRegistration()
				.getErrors()
				.getPolicy().getAccountCreationMissing());
	}

	private void deleteReservation(@NotNull String username) {
		String reservationKey = AccountReservationPersistenceService.usernameKey(username);
		if (reservationKey == null) return;

		accountReservationPersistenceService.delete(reservationKey);
	}

	private @Nullable UUID resolveNewAccountId(
			@NotNull RegistrationContext context,
			@NotNull AccountProviderProfile profile
	) {
		return uniqueIdGenerator.resolveConfiguredUniqueId(
				profile.getProviderUsername(),
				profile.getProviderSubject(),
				context.getIdentity().getObservedUniqueId()
		);
	}

	private record Creation(@Nullable Account account, @Nullable String refusal) {
	}
}
