package me.whereareiam.identica.common.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.migration.MigrationJourney;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.*;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.migration.MigrationPrecheckContext;
import me.whereareiam.identica.provider.migration.MigrationPrecheckResult;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultMigrationService implements MigrationService {
	private final ProviderManager providerManager;
	private final MigrationJourneyRegistry migrationJourneyRegistry;

	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final AccountPersistenceService accountPersistenceService;
	private final ConnectionCoordinator connectionCoordinator;

	private final SessionService sessionService;
	private final IdentityService identityService;

	private final DeliveryService deliveryService;

	private final Provider<Engine> engineProvider;
	private final Provider<Providers> providersProvider;
	private final Provider<Commands> commandsProvider;
	private final Provider<Messages> messagesProvider;

	private final Map<UUID, PendingConfirmationMigration> pending = new ConcurrentHashMap<>();

	@Override
	public @NotNull MigrationResult request(@NotNull MigrationRequest request) {
		UUID connectionUniqueId = request.getConnectionUniqueId();
		if (connectionUniqueId == null) return result(MigrationResultStatus.FAILED, null);

		PendingConfirmationMigration existing = pending.get(connectionUniqueId);
		if (existing != null) {
			if (!isExpired(existing)) {
				return result(MigrationResultStatus.PENDING_EXISTS, null);
			}
			pending.remove(connectionUniqueId);
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
		pending.put(connectionUniqueId, pendingMigration);

		return result(MigrationResultStatus.PENDING_CONFIRMATION, null);
	}

	@Override
	public @NotNull MigrationResult confirm(@NotNull MigrationConfirm confirm) {
		UUID connectionUniqueId = confirm.getConnectionUniqueId();
		if (connectionUniqueId == null)
			return result(MigrationResultStatus.FAILED, null);

		PendingConfirmationMigration pendingMigration = pending.get(connectionUniqueId);
		if (pendingMigration == null) return result(MigrationResultStatus.NO_PENDING, null);

		if (isExpired(pendingMigration)) {
			pending.remove(connectionUniqueId);
			return result(MigrationResultStatus.EXPIRED, null);
		}

		if (hasPendingMigration(connectionUniqueId)) {
			pending.remove(connectionUniqueId);
			return result(MigrationResultStatus.PENDING_EXISTS, null);
		}
		MigrationResult targetValidation = validateTargetProvider(pendingMigration);
		if (targetValidation != null) {
			pending.remove(connectionUniqueId);
			return targetValidation;
		}

		if (!isUsernameFree(pendingMigration.username(), pendingMigration.uniqueId())) {
			pending.remove(connectionUniqueId);
			return result(MigrationResultStatus.PRECHECK_DENIED, migrationLockedMessage());
		}

		MigrationPrecheckResult precheck = runPrechecks(pendingMigration);
		if (precheck != null && !precheck.isAllowed()) {
			pending.remove(connectionUniqueId);
			return result(MigrationResultStatus.PRECHECK_DENIED, precheck.getMessage());
		}

		UUID accountUniqueId = pendingMigration.uniqueId();
		if (accountUniqueId == null)
			accountUniqueId = connectionUniqueId;

		String targetProviderId = pendingMigration.targetProviderId();
		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, targetProviderId)
				.orElse(null);
		if (link != null && link.isPrimaryLink()) {
			pending.remove(connectionUniqueId);
			return result(MigrationResultStatus.ALREADY_PRIMARY, null);
		}

		String kickMessage = resolveKickMessage(confirm.getKickMessage(), precheck);

		MigrationResultStatus started = startJourney(pendingMigration, accountUniqueId);
		if (started == MigrationResultStatus.PENDING_EXISTS) pending.remove(connectionUniqueId);
		if (started != MigrationResultStatus.STARTED) return result(started, null);

		closeSession(accountUniqueId);
		disconnect(connectionUniqueId, kickMessage);
		pending.remove(connectionUniqueId);

		return result(MigrationResultStatus.STARTED, null);
	}

	@Override
	public @NotNull MigrationResult start(@NotNull MigrationStart start) {
		UUID accountUniqueId = resolveIdenticaUniqueId(start.getUniqueId(), start.getConnectionUniqueId());
		UUID connectionUniqueId = resolveIdenticaUniqueId(start.getConnectionUniqueId(), accountUniqueId);
		if (accountUniqueId == null) return result(MigrationResultStatus.FAILED, null);

		pending.remove(connectionUniqueId);
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

		if (!isUsernameFree(pendingMigration.username(), pendingMigration.uniqueId())) {
			return result(MigrationResultStatus.PRECHECK_DENIED, migrationLockedMessage());
		}

		MigrationPrecheckResult precheck = runPrechecks(pendingMigration);
		if (precheck != null && !precheck.isAllowed())
			return result(MigrationResultStatus.PRECHECK_DENIED, precheck.getMessage());

		AccountProviderLink link = providerLinkPersistenceService
				.findByUniqueIdAndProviderId(accountUniqueId, targetProviderId)
				.orElse(null);
		if (link != null && link.isPrimaryLink())
			return result(MigrationResultStatus.ALREADY_PRIMARY, null);

		String kickMessage = resolveKickMessage(start.getKickMessage(), precheck);

		MigrationResultStatus started = startJourney(pendingMigration, accountUniqueId);
		if (started != MigrationResultStatus.STARTED) return result(started, null);

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
			removed = pending.remove(connectionUniqueId) != null;
		}

		if (scope == MigrationCancelScope.PENDING || scope == MigrationCancelScope.ALL) {
			PendingMigration cancelled = connectionCoordinator.cancelMigrationJourney(connectionUniqueId)
					.toCompletableFuture()
					.join()
					.orElse(null);
			if (cancelled != null) {
				queueCancelledNotice(cancelled.getUniqueId());
				removed = true;
			}
		}

		return removed
				? result(MigrationResultStatus.CANCELLED, null)
				: result(MigrationResultStatus.NO_PENDING, null);
	}

	/**
	 * Starts the migration journey in the connection's turn, which also finds a journey another command started in
	 * the meantime.
	 */
	private @NotNull MigrationResultStatus startJourney(
			@NotNull PendingConfirmationMigration pendingMigration,
			@NotNull UUID accountUniqueId
	) {
		return connectionCoordinator.startMigrationJourney(MigrationJourney.builder()
				.connectionUniqueId(pendingMigration.connectionUniqueId())
				.accountUniqueId(accountUniqueId)
				.username(pendingMigration.username())
				.ip(pendingMigration.ip())
				.targetProviderId(pendingMigration.targetProviderId())
				.requestedAt(pendingMigration.requestedAt())
				.initiator(pendingMigration.initiator())
				.initiatorUniqueId(pendingMigration.initiatorUniqueId())
				.build()).toCompletableFuture().join();
	}

	private @Nullable MigrationResult validateTargetProvider(@NotNull PendingConfirmationMigration pendingMigration) {
		String targetProviderId = pendingMigration.targetProviderId();
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
		InternalProvider provider = resolveActiveProvider(pendingMigration.targetProviderId());
		Set<ProviderMigrationPrecheck> prechecks = provider != null ? provider.getMigrationPrechecks() : null;
		if (prechecks == null || prechecks.isEmpty())
			return MigrationPrecheckResult.allow();

		MigrationPrecheckContext context = MigrationPrecheckContext.builder()
				.uniqueId(pendingMigration.uniqueId())
				.connectionUniqueId(pendingMigration.connectionUniqueId())
				.username(pendingMigration.username())
				.ip(pendingMigration.ip())
				.providerId(pendingMigration.targetProviderId())
				.initiator(pendingMigration.initiator())
				.initiatorUniqueId(pendingMigration.initiatorUniqueId())
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
			if (provider == null || provider.getDescriptor() == null)
				continue;

			String id = provider.getDescriptor().getId();
			if (id.equalsIgnoreCase(providerId))
				return provider;
		}
		return null;
	}

	private boolean isConfiguredProvider(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return false;

		Providers providers = providersProvider.get();
		if (providers == null || providers.getProviders().isEmpty()) return false;

		for (Providers.ProviderEntry entry : providers.getProviders()) {
			if (entry == null || entry.getId().isBlank()) continue;
			if (entry.getId().equalsIgnoreCase(providerId))
				return true;
		}
		return false;
	}

	private boolean supportsMigrationJourney(
			@NotNull String providerId,
			@NotNull PendingConfirmationMigration pendingMigration
	) {
		JourneyPlan plan = migrationJourneyRegistry.resolvePlan(
				MigrationContext.builder()
						.connectionUniqueId(pendingMigration.connectionUniqueId())
						.identity(new ConnectionIdentity(
								resolveIdenticaUniqueId(pendingMigration.uniqueId(), pendingMigration.connectionUniqueId()),
								nonNull(pendingMigration.username()),
								pendingMigration.ip()
						))
						.targetProviderId(providerId)
						.build(),
				PipelineType.MIGRATION,
				engineProvider.get().getScenarios().getMigration().getJourneyMode(),
				providerId
		);

		for (JourneyPlan.StageEntry stage : plan.providerStages()) {
			if (!stage.steps().isEmpty())
				return true;
		}
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

	private boolean isExpired(@NotNull PendingConfirmationMigration pendingMigration) {
		long ttlMs = commandsProvider.get().getBehavior().getMigration().getConfirmTtl().toMillis();
		return ttlMs > 0 && pendingMigration.requestedAt() + ttlMs < System.currentTimeMillis();
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
		PendingConfirmationMigration pendingMigration = pending.get(connectionUniqueId);
		if (pendingMigration == null) return null;

		if (isExpired(pendingMigration)) {
			pending.remove(connectionUniqueId);
			return null;
		}

		return PendingMigration.builder()
				.uniqueId(pendingMigration.uniqueId())
				.connectionUniqueId(pendingMigration.connectionUniqueId())
				.targetProviderId(pendingMigration.targetProviderId())
				.requestedAt(pendingMigration.requestedAt())
				.initiator(pendingMigration.initiator())
				.initiatorUniqueId(pendingMigration.initiatorUniqueId())
				.phase(PendingMigration.Phase.CONFIRMATION)
				.build();
	}

	private @Nullable PendingMigration findStartedPendingMigration(@NotNull UUID connectionUniqueId) {
		return connectionCoordinator.findMigrationJourney(connectionUniqueId).toCompletableFuture().join().orElse(null);
	}

	private void queueCancelledNotice(@Nullable UUID accountUniqueId) {
		if (accountUniqueId == null) return;

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

	private @NotNull String resolveMigrationCancelledMessage() {
		return String.join("\n", messagesProvider.get().getScenarios().getMigration().getCancelled());
	}

	private record PendingConfirmationMigration(
			UUID uniqueId,
			UUID connectionUniqueId,
			String targetProviderId,
			String username,
			String ip,
			MigrationInitiator initiator,
			UUID initiatorUniqueId,
			long requestedAt
	) {
	}
}
