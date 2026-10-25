package me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.migration.IdentityState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApplyProviderLinkPhase implements PipelinePhase<IdentityState> {
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ProviderProfilePersistenceService providerProfilePersistenceService;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "apply-provider-provider";
	}

	@Override
	public int order() {
		return 400;
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

		Account account = state.getAccount();
		AccountProviderProfile profile = state.getProfile();
		if (account == null || profile == null) {
			state.setResult(PipelineResult.failed(providerLinkMissingMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		String providerId = profile.getProviderId();
		String providerSubject = profile.getProviderSubject();
		Optional<AccountProviderLink> existingBySubject = providerLinkPersistenceService.findBySubject(providerId, providerSubject);
		if (existingBySubject.isPresent()
				&& !existingBySubject.get().getUniqueId().equals(account.getUniqueId())) {
			state.setResult(PipelineResult.denied(migrationFailedMessage()));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		long now = System.currentTimeMillis();
		AccountProviderLink link = AccountProviderLink.builder()
				.uniqueId(account.getUniqueId())
				.providerId(providerId)
				.providerSubject(providerSubject)
				.primaryLink(true)
				.linkedAt(now)
				.lastSeenAt(now)
				.build();

		AccountProviderLink stored = providerLinkPersistenceService.upsert(link);
		providerProfilePersistenceService.upsert(profile);
		providerLinkPersistenceService.setPrimaryExclusive(account.getUniqueId(), providerId);

		state.setLink(stored.toBuilder().primaryLink(true).build());
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	private @NotNull PlayerMessage migrationFailedMessage() {
		return ScenarioMessages.of(PipelineType.MIGRATION).getFailed().create(messagesProvider.get());
	}

	private @NotNull PlayerMessage providerLinkMissingMessage() {
		return ScenarioMessages.of(PipelineType.MIGRATION).getProviderLinkMissing().create(messagesProvider.get());
	}

}
