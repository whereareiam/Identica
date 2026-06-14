package me.whereareiam.identica.common.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.migration.confirmation.MigrationConfirmationStore;
import me.whereareiam.identica.common.migration.confirmation.PendingConfirmationMigration;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.*;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.migration.MigrationPrecheckContext;
import me.whereareiam.identica.provider.migration.MigrationPrecheckResult;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultMigrationService implements MigrationService {
	private final ProviderManager providerManager;
	private final MigrationJourneyRegistry migrationJourneyRegistry;

	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final AccountPersistenceService accountPersistenceService;
	private final PipelineStateStore pipelineStateStore;

	private final SessionService sessionService;
	private final IdentityService identityService;

	private final DeliveryService deliveryService;
	private final EventManager eventManager;

	private final Provider<Engine> engineProvider;
	private final Provider<Providers> providersProvider;
	private final Provider<Messages> messagesProvider;
	private final MigrationConfirmationStore migrationConfirmationStore;

	@Override
	public @NotNull MigrationResult request(@NotNull MigrationRequest request) {
		UUID connectionUniqueId = request.getConnectionUniqueId();
		if (connectionUniqueId == null) return result(MigrationResultStatus.FAILED, null);

		PendingConfirmationMigration existing = migrationConfirmationStore.find(connectionUniqueId).orElse(null);
		if (existing != null) {
			if (!migrationConfirmationStore.isExpired(existing)) return result(MigrationResultStatus.PENDING_EXISTS, null);
			migrationConfirmationStore.clear(connectionUniqueId);
		}

		if (hasPendingMigration(connectionUniqueId)) return result(MigrationResultStatus.PENDING_EXISTS, null);

		String targetProviderId = normalize(request.getTargetProviderId());
		if (targetProviderId == null) return result(MigrationResultStatus.FAILED, null);

		UUID accountUniqueId = resolveIdenticaUniqueId(request.getAccountUniqueId(), connectionUniqueId);

		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, targetProviderId)
				.orElse(null);
		if (link != null && link.isPrimaryLink()) return result(MigrationResultStatus.ALREADY_PRIMARY, null);

		PendingConfirmationMigration pendingMigration = new PendingConfirmationMigration(
				accountUniqueId,
				connectionUniqueId,
				targetProviderId,
				normalize(request.getUsername()),
				normalize(request.getIp()),
				request.getInitiator(),
				request.getInitiatorUniqueId(),
				System.currentTimeMillis()
		);
		MigrationResult targetValidation = validateTargetProvider(pendingMigration);
		if (targetValidation != null) return targetValidation;
		migrationConfirmationStore.put(pendingMigration);

		return result(MigrationResultStatus.PENDING_CONFIRMATION, null);
	}

	@Override
	public @NotNull MigrationResult confirm(@NotNull MigrationConfirm confirm) {
		UUID connectionUniqueId = confirm.getConnectionUniqueId();
		if (connectionUniqueId == null) return result(MigrationResultStatus.FAILED, null);

		PendingConfirmationMigration pendingMigration = migrationConfirmationStore.find(connectionUniqueId).orElse(null);
		if (pendingMigration == null) return result(MigrationResultStatus.NO_PENDING, null);

		if (migrationConfirmationStore.isExpired(pendingMigration)) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return result(MigrationResultStatus.EXPIRED, null);
		}

		if (hasPendingMigration(connectionUniqueId)) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return result(MigrationResultStatus.PENDING_EXISTS, null);
		}
		MigrationResult targetValidation = validateTargetProvider(pendingMigration);
		if (targetValidation != null) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return targetValidation;
		}

		if (!isUsernameFree(pendingMigration.getUsername(), pendingMigration.getUniqueId())) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return result(MigrationResultStatus.PRECHECK_DENIED, migrationLockedMessage());
		}

		MigrationPrecheckResult precheck = runPrechecks(pendingMigration);
		if (precheck != null && !precheck.isAllowed()) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return result(MigrationResultStatus.PRECHECK_DENIED, precheck.getMessage());
		}

		UUID accountUniqueId = pendingMigration.getUniqueId();
		if (accountUniqueId == null)
			accountUniqueId = connectionUniqueId;

		String targetProviderId = pendingMigration.getTargetProviderId();
		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, targetProviderId)
				.orElse(null);
		if (link != null && link.isPrimaryLink()) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return result(MigrationResultStatus.ALREADY_PRIMARY, null);
		}

		String kickMessage = resolveKickMessage(confirm.getKickMessage(), precheck);

		boolean stored = storePendingMigration(pendingMigration, accountUniqueId);
		if (!stored) return result(MigrationResultStatus.FAILED, null);

		closeSession(accountUniqueId);
		disconnect(connectionUniqueId, kickMessage);
		migrationConfirmationStore.clear(connectionUniqueId);

		return result(MigrationResultStatus.STARTED, null);
	}

	@Override
	public @NotNull MigrationResult start(@NotNull MigrationStart start) {
		UUID accountUniqueId = resolveIdenticaUniqueId(start.getUniqueId(), start.getConnectionUniqueId());
		UUID connectionUniqueId = resolveIdenticaUniqueId(start.getConnectionUniqueId(), accountUniqueId);
		if (accountUniqueId == null) return result(MigrationResultStatus.FAILED, null);

		migrationConfirmationStore.clear(connectionUniqueId);
		if (hasPendingMigration(connectionUniqueId)) return result(MigrationResultStatus.PENDING_EXISTS, null);

		String targetProviderId = normalize(start.getTargetProviderId());
		if (targetProviderId == null) return result(MigrationResultStatus.FAILED, null);

		PendingConfirmationMigration pendingMigration = new PendingConfirmationMigration(
				accountUniqueId,
				connectionUniqueId,
				targetProviderId,
				resolveUsername(start.getUsername(), accountUniqueId),
				normalize(start.getIp()),
				start.getInitiator(),
				start.getInitiatorUniqueId(),
				System.currentTimeMillis()
		);
		MigrationResult targetValidation = validateTargetProvider(pendingMigration);
		if (targetValidation != null) return targetValidation;

		if (!isUsernameFree(pendingMigration.getUsername(), pendingMigration.getUniqueId())) {
			return result(MigrationResultStatus.PRECHECK_DENIED, migrationLockedMessage());
		}

		MigrationPrecheckResult precheck = runPrechecks(pendingMigration);
		if (precheck != null && !precheck.isAllowed())
			return result(MigrationResultStatus.PRECHECK_DENIED, precheck.getMessage());

		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, targetProviderId)
				.orElse(null);
		if (link != null && link.isPrimaryLink()) return result(MigrationResultStatus.ALREADY_PRIMARY, null);

		String kickMessage = resolveKickMessage(start.getKickMessage(), precheck);

		boolean stored = storePendingMigration(pendingMigration, accountUniqueId);
		if (!stored)
			return result(MigrationResultStatus.FAILED, null);

		closeSession(accountUniqueId);
		disconnect(connectionUniqueId, kickMessage);
		return result(MigrationResultStatus.STARTED, null);
	}

	@Override
	public @NotNull MigrationResult cancel(@NotNull MigrationCancel cancel) {
		UUID connectionUniqueId = cancel.getConnectionUniqueId();
		if (connectionUniqueId == null) return result(MigrationResultStatus.FAILED, null);

		MigrationCancelScope scope = cancel.getScope() != null ? cancel.getScope() : MigrationCancelScope.CONFIRMATION;
		boolean removed = false;

		if (scope == MigrationCancelScope.CONFIRMATION || scope == MigrationCancelScope.ALL) {
			removed = migrationConfirmationStore.consume(connectionUniqueId).isPresent();
		}

		if (scope == MigrationCancelScope.PENDING || scope == MigrationCancelScope.ALL) {
			PipelineStateReference reference = PipelineStateReference.builder()
					.connectionUniqueId(connectionUniqueId)
					.build();
			PipelineState stored = pipelineStateStore.find(reference).orElse(null);
			if (stored != null && stored.item(MigrationPendingState.class).isPresent()) {
				queueCancelledNotice(resolvePendingAccountUniqueId(stored), stored);
				MigrationContext context = (MigrationContext) stored.getScenario(PipelineType.MIGRATION);
				UUID resolvedConnectionUniqueId = context != null ? context.getConnectionUniqueId() : null;
				if (context != null && resolvedConnectionUniqueId != null) {
					eventManager.call(new MigrationResolvedEvent(context, ScenarioResolution.CANCELLED, false));
				}
				pipelineStateStore.clear(reference);
				removed = true;
			}
		}

		return removed
				? result(MigrationResultStatus.CANCELLED, null)
				: result(MigrationResultStatus.NO_PENDING, null);
	}

	private boolean storePendingMigration(@NotNull PendingConfirmationMigration pendingMigration, @NotNull UUID accountUniqueId) {
		long ttlMs = engineProvider.get().getScenarios().getMigration().pipelineTtlMillis();
		if (ttlMs <= 0) return false;

		JourneyMode journeyMode = engineProvider.get().getScenarios().getMigration().getJourneyMode();
		MigrationContext context = MigrationContext.builder()
				.connectionUniqueId(pendingMigration.getConnectionUniqueId())
				.identity(new ConnectionIdentity(
						accountUniqueId,
						nonNull(pendingMigration.getUsername()),
						pendingMigration.getIp()
				))
				.targetProviderId(pendingMigration.getTargetProviderId())
				.build();

		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.MIGRATION);
		pipelineState.setScenario(context);
		pipelineState.putItem(new MigrationPendingState(
				pendingMigration.getTargetProviderId(),
				pendingMigration.getRequestedAt(),
				pendingMigration.getInitiator(),
				pendingMigration.getInitiatorUniqueId()
		), ttlMs);
		pipelineState.putItem(new JourneyStateItem(journeyMode, null, 0), ttlMs);

		PipelineStateReference reference = PipelineStateReference.from(context);
		pipelineStateStore.save(reference, pipelineState, ttlMs);
		UUID connectionUniqueId = context.getConnectionUniqueId();
		if (connectionUniqueId != null) {
			eventManager.call(new MigrationRequiredEvent(context, false, System.currentTimeMillis() + ttlMs, journeyMode));
		}
		Logger.debug(
				"Stored migration pending connection=%s identica=%s target=%s username=%s ip=%s journeyMode=%s",
				pendingMigration.getConnectionUniqueId(),
				accountUniqueId,
				pendingMigration.getTargetProviderId(),
				pendingMigration.getUsername(),
				pendingMigration.getIp(),
				journeyMode
		);

		return true;
	}

	private @Nullable MigrationResult validateTargetProvider(@NotNull PendingConfirmationMigration pendingMigration) {
		String targetProviderId = pendingMigration.getTargetProviderId();
		if (targetProviderId == null || targetProviderId.isBlank())
			return result(MigrationResultStatus.FAILED, null);

		InternalProvider provider = resolveActiveProvider(targetProviderId);
		if (provider == null) {
			return isConfiguredProvider(targetProviderId)
					? result(MigrationResultStatus.PROVIDER_UNAVAILABLE, null)
					: result(MigrationResultStatus.TARGET_UNSUPPORTED, null);
		}
		
		if (!supportsMigrationJourney(targetProviderId, pendingMigration))
			return result(MigrationResultStatus.TARGET_UNSUPPORTED, null);

		return null;
	}

	private boolean hasPendingMigration(@NotNull UUID connectionUniqueId) {
		return findStartedPendingMigration(connectionUniqueId) != null;
	}

	private MigrationPrecheckResult runPrechecks(@NotNull PendingConfirmationMigration pendingMigration) {
		InternalProvider provider = resolveActiveProvider(pendingMigration.getTargetProviderId());
		Set<ProviderMigrationPrecheck> prechecks = provider != null ? provider.getMigrationPrechecks() : null;
		if (prechecks == null || prechecks.isEmpty()) return MigrationPrecheckResult.allow();

		MigrationPrecheckContext context = MigrationPrecheckContext.builder()
				.uniqueId(pendingMigration.getUniqueId())
				.connectionUniqueId(pendingMigration.getConnectionUniqueId())
				.username(pendingMigration.getUsername())
				.ip(pendingMigration.getIp())
				.providerId(pendingMigration.getTargetProviderId())
				.initiator(pendingMigration.getInitiator())
				.initiatorUniqueId(pendingMigration.getInitiatorUniqueId())
				.build();

		String kickMessage = null;
		for (ProviderMigrationPrecheck precheck : prechecks) {
			if (precheck == null) continue;

			MigrationPrecheckResult result = precheck.precheck(context);
			if (!result.isAllowed()) return result;

			if (kickMessage == null && result.getKickMessage() != null && !result.getKickMessage().isBlank())
				kickMessage = result.getKickMessage();
		}

		return MigrationPrecheckResult.allow(kickMessage);
	}

	private @Nullable InternalProvider resolveActiveProvider(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return null;

		for (InternalProvider provider : providerManager.getProviders()) {
			if (provider == null || provider.getDescriptor() == null) continue;

			String id = provider.getDescriptor().getId();
			if (id.equalsIgnoreCase(providerId)) return provider;
		}
		return null;
	}

	private boolean isConfiguredProvider(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return false;

		Providers providers = providersProvider.get();
		if (providers == null || providers.getProviders().isEmpty()) return false;

		for (Providers.ProviderEntry entry : providers.getProviders()) {
			if (entry == null || entry.getId().isBlank()) continue;
			if (entry.getId().equalsIgnoreCase(providerId)) return true;
		}
		return false;
	}

	private boolean supportsMigrationJourney(
			@NotNull String providerId,
			@NotNull PendingConfirmationMigration pendingMigration
	) {
		JourneyPlan plan = migrationJourneyRegistry.resolvePlan(
				MigrationContext.builder()
						.connectionUniqueId(pendingMigration.getConnectionUniqueId())
						.identity(new ConnectionIdentity(
								resolveIdenticaUniqueId(pendingMigration.getUniqueId(), pendingMigration.getConnectionUniqueId()),
								nonNull(pendingMigration.getUsername()),
								pendingMigration.getIp()
						))
						.targetProviderId(providerId)
						.build(),
				PipelineType.MIGRATION,
				engineProvider.get().getScenarios().getMigration().getJourneyMode(),
				providerId
		);

		for (JourneyPlan.StageEntry stage : plan.providerStages())
			if (!stage.steps().isEmpty()) return true;

		return false;
	}

	private void closeSession(@Nullable UUID accountUniqueId) {
		if (accountUniqueId == null) return;

		sessionService.close(accountUniqueId).join();
	}

	private void disconnect(@NotNull UUID connectionUniqueId, @Nullable String message) {
		String resolved = message != null ? message : "";
		Identity identity = identityService.findByConnectionUniqueId(connectionUniqueId).orElse(null);
		if (identity == null) return;

		identity.disconnect(Serializer.serialize(identity, resolved));
	}

	private @NotNull MigrationResult result(@NotNull MigrationResultStatus status, @Nullable String message) {
		return MigrationResult.builder()
				.status(status)
				.message(message)
				.build();
	}

	private UUID resolveIdenticaUniqueId(@Nullable UUID accountUniqueId, @Nullable UUID fallback) {
		return accountUniqueId != null ? accountUniqueId : fallback;
	}

	private boolean isUsernameFree(@Nullable String username, @Nullable UUID currentUniqueId) {
		String normalized = normalize(username);
		if (normalized == null) return true;

		for (Account account : accountPersistenceService.findByUsername(normalized)) {
			if (account == null) continue;
			if (currentUniqueId != null && currentUniqueId.equals(account.getUniqueId()))
				continue;

			String existing = account.getUsername();
			if (normalized.equalsIgnoreCase(existing)) return false;
		}

		return true;
	}

	private @NotNull String migrationLockedMessage() {
		return messagesProvider.get().getCommands().getMigration().getLocked();
	}

	private @Nullable String resolveUsername(@Nullable String username, @NotNull UUID accountUniqueId) {
		String normalized = normalize(username);
		if (normalized != null) return normalized;

		Account account = accountPersistenceService.findByUniqueId(accountUniqueId).orElse(null);
		if (account == null) return null;

		return normalize(account.getUsername());
	}

	private @Nullable String resolveKickMessage(@Nullable String requested, @Nullable MigrationPrecheckResult precheck) {
		if (precheck != null && precheck.getKickMessage() != null && !precheck.getKickMessage().isBlank())
			return precheck.getKickMessage();

		return normalize(requested);
	}

	private @Nullable String normalize(@Nullable String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isBlank() ? null : trimmed;
	}

	private @NotNull String nonNull(@Nullable String value) {
		return value == null ? "" : value;
	}

	@Override
	public @NotNull Optional<PendingMigration> findPendingMigration(@NotNull UUID connectionUniqueId) {
		PendingMigration confirmation = findConfirmationPendingMigration(connectionUniqueId);
		if (confirmation != null) return Optional.of(confirmation);

		return Optional.ofNullable(findStartedPendingMigration(connectionUniqueId));
	}

	private @Nullable PendingMigration findConfirmationPendingMigration(@NotNull UUID connectionUniqueId) {
		PendingConfirmationMigration pendingMigration = migrationConfirmationStore.find(connectionUniqueId).orElse(null);
		if (pendingMigration == null) return null;

		if (migrationConfirmationStore.isExpired(pendingMigration)) {
			migrationConfirmationStore.clear(connectionUniqueId);
			return null;
		}

		return PendingMigration.builder()
				.uniqueId(pendingMigration.getUniqueId())
				.connectionUniqueId(pendingMigration.getConnectionUniqueId())
				.targetProviderId(pendingMigration.getTargetProviderId())
				.requestedAt(pendingMigration.getRequestedAt())
				.initiator(pendingMigration.getInitiator())
				.initiatorUniqueId(pendingMigration.getInitiatorUniqueId())
				.phase(PendingMigration.Phase.CONFIRMATION)
				.build();
	}

	private @Nullable PendingMigration findStartedPendingMigration(@NotNull UUID connectionUniqueId) {
		PipelineStateReference reference = PipelineStateReference.builder()
				.connectionUniqueId(connectionUniqueId)
				.build();

		PipelineState state = pipelineStateStore.find(reference).orElse(null);
		if (state == null) {
			Logger.debug("Migration pending lookup missed connection=%s", connectionUniqueId);
			return null;
		}

		MigrationPendingState pendingState = state.item(MigrationPendingState.class).orElse(null);
		if (pendingState == null) {
			Logger.debug(
					"Migration pending lookup found state without pending marker connection=%s pipeline=%s",
					connectionUniqueId,
					state.getPipelineType()
			);
			return null;
		}

		MigrationContext context = (MigrationContext) state.getScenario(PipelineType.MIGRATION);
		UUID uniqueId = null;
		UUID storedConnectionUniqueId = connectionUniqueId;
		String targetProviderId = pendingState.getTargetProviderId();
		if (context != null) {
			if (context.getConnectionUniqueId() != null)
				storedConnectionUniqueId = context.getConnectionUniqueId();
			targetProviderId = context.getTargetProviderId();
			uniqueId = context.getIdentity().getAccountUniqueId();
		}
		Logger.debug(
				"Migration pending lookup found connection=%s storedConnection=%s identica=%s target=%s",
				connectionUniqueId,
				storedConnectionUniqueId,
				uniqueId,
				targetProviderId
		);

		return PendingMigration.builder()
				.uniqueId(uniqueId)
				.connectionUniqueId(storedConnectionUniqueId)
				.targetProviderId(targetProviderId)
				.requestedAt(pendingState.getRequestedAt())
				.initiator(pendingState.getInitiator())
				.initiatorUniqueId(pendingState.getInitiatorUniqueId())
				.phase(PendingMigration.Phase.STARTED)
				.build();
	}

	private void queueCancelledNotice(@Nullable UUID accountUniqueId, @Nullable PipelineState state) {
		if (accountUniqueId == null || state == null) return;
		if (state.getPipelineType() != PipelineType.MIGRATION) return;
		if (state.item(MigrationPendingState.class).isEmpty()) return;

        deliveryService.queue(DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.NOTICE)
				.target(DeliveryTarget.builder()
						.accountUniqueId(accountUniqueId)
						.build())
				.payload(DeliveryPayload.builder()
						.chatMessage(resolveMigrationCancelledMessage())
						.build())
				.checkpoint(DeliveryCheckpoint.PLATFORM_READY_INITIAL)
				.semantics(DeliverySemantics.ONCE)
				.createdAt(System.currentTimeMillis())
				.updatedAt(System.currentTimeMillis())
				.build());
	}

	private @Nullable UUID resolvePendingAccountUniqueId(@Nullable PipelineState state) {
		if (state == null) return null;

		MigrationContext context = (MigrationContext) state.getScenario(PipelineType.MIGRATION);
		if (context != null && context.getAccountUniqueId() != null) return context.getAccountUniqueId();

		return null;
	}

	private @NotNull String resolveMigrationCancelledMessage() {
		return String.join("\n", messagesProvider.get().getScenarios().getMigration().getCancelled());
	}

}
