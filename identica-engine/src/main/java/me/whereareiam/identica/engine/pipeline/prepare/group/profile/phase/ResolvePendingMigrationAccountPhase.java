package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
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
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecisionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Decides whether a connection continues the pending migration stored for its username and IP.
 *
 * <p>The migration continues only when a provider verified the subject of the connection, and that subject is
 * either the one the account is linked with, so the provider it is leaving, or belongs to the migration target
 * itself. After proof with the provider being left, the target provider is selected and set up in the same
 * session. Every other connection cancels the migration. The rule names no provider: it relies on
 * {@link ProviderContext#isSubjectVerified()}, which each provider's subject resolver supplies.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResolvePendingMigrationAccountPhase implements PipelinePhase<PrepareGroupState> {
	private final PipelineStateStore pipelineStateStore;
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final DeliveryService deliveryService;
	private final EventManager eventManager;
	private final ProviderOperations providerOperations;
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "resolve-pending-migration-account";
	}

	@Override
	public int order() {
		return 150;
	}

	@Override
	public @NotNull Class<PrepareGroupState> stateType() {
		return PrepareGroupState.class;
	}

	@Override
	public boolean supports(@NotNull PipelineState pipelineState, @NotNull PrepareGroupState state) {
		if (pipelineState.item(PrepareDecisionItem.class).isPresent()) return false;
		if (pipelineState.item(PrepareAccountCandidateItem.class).isPresent()) return false;

		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(null);
		return context != null
				&& context.getProvider() != null
				&& context.getProvider().getProviderId() != null
				&& !context.getProvider().getProviderId().isBlank();
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<PrepareGroupState>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull PrepareGroupState state
	) {
		PrepareContextItem context = pipelineState.item(PrepareContextItem.class).orElse(new PrepareContextItem());
		var request = state.getRequest();
		ProviderContext provider = context.getProvider();
		if (request == null || provider == null) {
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		String connectionKey = request.getConnectionKey();
		String providerId = provider.getProviderId();
		String requestedUsername = request.getIdentity().getUsername();
		if (connectionKey == null || connectionKey.isBlank() || providerId == null || providerId.isBlank()) {
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		PipelineState pendingState = pipelineStateStore.find(PipelineStateReference.builder()
				.connectionKey(connectionKey)
				.build()).orElse(null);
		if (pendingState == null || pendingState.getPipelineType() != PipelineType.MIGRATION)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		if (pendingState.item(MigrationPendingState.class).isEmpty())
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		MigrationContext migration = (MigrationContext) pendingState.getScenario(PipelineType.MIGRATION);
		if (migration == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		String targetProviderId = migration.getTargetProviderId();
		if (targetProviderId == null) return CompletableFuture.completedFuture(PhaseResult.pass(state));

		UUID accountUniqueId = migration.getAccountUniqueId();
		boolean target = targetProviderId.equalsIgnoreCase(providerId);
		boolean proven = provider.isSubjectVerified() && (target || isLinkedSubject(accountUniqueId, provider));
		if (!proven) {
			cancel(migration, connectionKey, targetProviderId, provider);
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		if (!target) {
			SubjectResolution resolved = providerOperations.resolveSelectedSubject(
					targetProviderId,
					SubjectResolveContext.builder()
							.identity(request.getIdentity())
							.build()
			);
			context.setProvider(ProviderContext.of(
					targetProviderId,
					resolved != null ? resolved.getProviderSubject() : null,
					requestedUsername,
					ProviderOrigin.MANUAL
			));
			pipelineState.putItem(context, 0L);
			Logger.debug(
					"Prepare continued pending migration after proof with the provider it leaves requested=%s proven=%s key=%s",
					targetProviderId,
					providerId,
					connectionKey
			);
		}

		if (accountUniqueId == null)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		Account storedAccount = accountPersistenceService.findByUniqueId(accountUniqueId).orElse(null);
		Account account = storedAccount != null
				? storedAccount.toBuilder().username(requestedUsername).build()
				: Account.builder()
						.uniqueId(accountUniqueId)
						.username(requestedUsername)
						.build();

		pipelineState.putItem(new PrepareAccountCandidateItem(
				accountUniqueId,
				null,
				account,
				null,
				null
		), 0L);
		Logger.debug(
				"Prepare reusing pending migration account provider=%s identica=%s key=%s",
				providerId,
				accountUniqueId,
				connectionKey
		);

		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	/**
	 * Whether the provider verified the connection as the subject the migrating account is linked with. That
	 * is the provider the account is leaving, whichever provider it is.
	 */
	private boolean isLinkedSubject(@Nullable UUID accountUniqueId, @NotNull ProviderContext provider) {
		String subject = provider.getProviderSubject();
		if (accountUniqueId == null || provider.getProviderId() == null || subject == null || subject.isBlank())
			return false;

		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, provider.getProviderId())
				.orElse(null);
		return link != null && subject.equalsIgnoreCase(link.getProviderSubject());
	}

	private void cancel(
			@NotNull MigrationContext migration,
			@NotNull String connectionKey,
			@NotNull String targetProviderId,
			@NotNull ProviderContext provider
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
				"Prepare cleared pending migration without proof requested=%s observed=%s verified=%s key=%s",
				targetProviderId,
				provider.getProviderId(),
				provider.isSubjectVerified(),
				connectionKey
		);
	}
}
