package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Decides whether a connection continues the pending migration that claims it.
 *
 * <p>The migration continues only when a provider verified the subject of the connection, and that subject is
 * either the one the account is linked with, so the provider it is leaving, or belongs to the migration target
 * itself. The rule names no provider: it relies on the verified subject claims of the selection, which each
 * provider's subject resolver supplies. Every other connection cancels the migration and withdraws its claim, so the
 * next claim of the selection wins.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolvePendingMigrationAccountPhase implements PipelinePhase<PrepareGroupState> {
	private final PipelineStateStore pipelineStateStore;
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final DeliveryService deliveryService;
	private final EventManager eventManager;
	private final ProviderSelector providerSelector;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "resolve-pending-migration-account";
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public @NotNull Class<PrepareGroupState> stateType() {
		return PrepareGroupState.class;
	}

	@Override
	public boolean supports(@NotNull PipelineState pipelineState, @NotNull PrepareGroupState state) {
		if (pipelineState.has(PrepareDecision.KEY)) return false;
		if (pipelineState.has(PrepareAccountCandidateItem.KEY)) return false;
		if (state.getRequest() == null || !state.getRequest().getStage().includesProfile()) return false;

		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(null);
		return context != null && context.getSelection().getOrigin() == ProviderClaimOrigin.MIGRATION;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		PrepareContextItem context = pipelineState.get(PrepareContextItem.KEY).orElse(new PrepareContextItem());
		var request = state.getRequest();
		ProviderSelection selection = context.getSelection();
		String connectionKey = request != null ? request.getConnectionKey() : null;
		if (connectionKey == null || connectionKey.isBlank())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		PipelineState pendingState = pipelineStateStore.find(PipelineStateReference.builder()
				.connectionKey(connectionKey)
				.build()).orElse(null);
		if (pendingState == null || pendingState.getPipelineType() != PipelineType.MIGRATION)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		if (pendingState.get(MigrationPendingState.KEY).isEmpty())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		MigrationContext migration = (MigrationContext) pendingState.getScenario(PipelineType.MIGRATION);
		if (migration == null || migration.getTargetProviderId() == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String targetProviderId = migration.getTargetProviderId();
		UUID accountUniqueId = migration.getAccountUniqueId();
		if (!isProven(selection, targetProviderId, accountUniqueId)) {
			cancel(migration, connectionKey, targetProviderId, selection);
			context.setSelection(providerSelector.revoke(selection, ProviderClaimOrigin.MIGRATION));
			pipelineState.put(PrepareContextItem.KEY, context);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (accountUniqueId == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String requestedUsername = request.getIdentity().getUsername();
		Account storedAccount = accountPersistenceService.findByUniqueId(accountUniqueId).orElse(null);
		Account account = storedAccount != null
				? storedAccount.toBuilder().username(requestedUsername).build()
				: Account.builder()
						.uniqueId(accountUniqueId)
						.username(requestedUsername)
						.build();

		pipelineState.put(PrepareAccountCandidateItem.KEY, new PrepareAccountCandidateItem(
				accountUniqueId,
				null,
				account,
				null,
				null
		));
		Logger.debug(
				"Prepare reusing pending migration account provider=%s identica=%s key=%s",
				targetProviderId,
				accountUniqueId,
				connectionKey
		);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Whether a provider verified the connection as a subject of the target, or as the subject the migrating account
	 * is linked with, which is the provider the account is leaving, whichever provider it is.
	 */
	private boolean isProven(
			@NotNull ProviderSelection selection,
			@NotNull String targetProviderId,
			@Nullable UUID accountUniqueId
	) {
		if (selection.findVerifiedSubject(targetProviderId).isPresent()) return true;

		return selection.claims().stream()
				.filter(ProviderClaim::hasProof)
				.anyMatch(claim -> isLinkedSubject(accountUniqueId, claim));
	}

	private boolean isLinkedSubject(@Nullable UUID accountUniqueId, @NotNull ProviderClaim claim) {
		if (accountUniqueId == null) return false;

		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, claim.getProviderId())
				.orElse(null);
		return link != null && link.getProviderSubject().equalsIgnoreCase(claim.getVerifiedSubject());
	}

	private void cancel(
			@NotNull MigrationContext migration,
			@NotNull String connectionKey,
			@NotNull String targetProviderId,
			@NotNull ProviderSelection selection
	) {
		UUID cancelledAccountUniqueId = migration.getAccountUniqueId();
		String notice = String.join("\n", messagesProvider.get().getScenarios().getMigration().getCancelled());
		if (cancelledAccountUniqueId != null && !notice.isBlank()) {
			long now = System.currentTimeMillis();
			deliveryService.queue(DeliveryRequest.builder()
					.id(UUID.randomUUID())
					.source(DeliverySource.NOTICE)
					.target(DeliveryTarget.builder()
							.accountUniqueId(cancelledAccountUniqueId)
							.build())
					.payload(DeliveryPayload.builder()
							.chatMessage(notice)
							.build())
					.checkpoint(DeliveryCheckpoint.PLATFORM_READY_INITIAL)
					.semantics(DeliverySemantics.ONCE)
					.createdAt(now)
					.updatedAt(now)
					.build());
		}

		if (migration.getConnectionUniqueId() != null)
			eventManager.call(new MigrationResolvedEvent(migration, ScenarioResolution.CANCELLED, false));

		pipelineStateStore.clear(PipelineStateReference.builder()
				.connectionKey(connectionKey)
				.build());
		Logger.debug(
				"Prepare cleared pending migration without proof requested=%s claims=%s key=%s",
				targetProviderId,
				selection.claims(),
				connectionKey
		);
	}
}
