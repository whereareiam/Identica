package me.whereareiam.identica.engine.pipeline.scenario;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.event.scenario.ScenarioRequiredEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.event.scenario.registration.RegistrationResolvedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.model.scheduler.DelayedRunnableTask;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Singleton
public class PendingPipelineKickCoordinator implements EventListener, RuntimeLifecycle {
	private static final Origin ORIGIN = Origin.core(PendingPipelineKickCoordinator.class);
	private static final Purpose PURPOSE = Purpose.of("pipeline-expiry");

	private final Provider<Messages> messagesProvider;
	private final IdentityService identityService;
	private final DeliveryService deliveryService;
	private final EventManager eventManager;
	private final Scheduler scheduler;

	@Inject
	public PendingPipelineKickCoordinator(
			Provider<Messages> messagesProvider,
			IdentityService identityService,
			DeliveryService deliveryService,
			Scheduler scheduler,
			EventManager eventManager
	) {
		this.messagesProvider = messagesProvider;
		this.identityService = identityService;
		this.deliveryService = deliveryService;
		this.eventManager = eventManager;
		this.scheduler = scheduler;
		eventManager.register(this);
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	/**
	 * Has nothing to start: a kick is scheduled when a scenario is required.
	 */
	@Override
	public void initialize() {
	}

	/**
	 * Cancels the kicks still waiting.
	 */
	@Override
	public void shutdown() {
		scheduler.cancelByOrigin(ORIGIN);
	}

	@IdenticEvent
	public void onScenarioRequired(@NotNull ScenarioRequiredEvent event) {
		PipelineType type = resolvePipelineType(event.getContext());
		if (type == null) return;

		long delayMs = Math.max(0L, event.getExpiresAt() - System.currentTimeMillis());
		scheduleKick(jobKey(type, event), delayMs, type, event.getContext());
	}

	@IdenticEvent
	public void onScenarioResolved(@NotNull ScenarioResolvedEvent event) {
		PipelineType type = resolvePipelineType(event.getContext());
		if (type == null) return;

		scheduler.cancel(jobKey(type, event));
	}

	private void scheduleKick(
			@NotNull JobKey key,
			long delayMs,
			@NotNull PipelineType type,
			@NotNull ScenarioContext context
	) {
		DelayedRunnableTask task = DelayedRunnableTask.builder()
				.key(key)
				.delay(delayMs)
				.runnable(() -> disconnectExpired(type, context))
				.build();

		scheduler.schedule(task);
	}

	private void disconnectExpired(@NotNull PipelineType type, @NotNull ScenarioContext context) {
		emitResolved(context);

		Identity identity = resolveIdentity(context);
		queueCancelledNotice(type, context, identity);
		if (identity == null) return;

		String message = resolveExpiredMessage(type);
		identity.disconnect(Serializer.serialize(identity, message));
	}

	private void queueCancelledNotice(@NotNull PipelineType type, @NotNull ScenarioContext context, @Nullable Identity identity) {
		if (type != PipelineType.MIGRATION) return;

		UUID accountUniqueId = context.getAccountUniqueId();
		if (accountUniqueId == null && identity != null) accountUniqueId = identity.getAccountUniqueId();
		if (accountUniqueId == null) return;

		deliveryService.queue(DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.NOTICE)
				.target(DeliveryTarget.builder()
						.accountUniqueId(accountUniqueId)
						.build())
				.payload(DeliveryPayload.builder()
						.chatMessage(String.join("\n", messagesProvider.get().getScenarios().getMigration().getCancelled()))
						.build())
				.checkpoint(DeliveryCheckpoint.PLATFORM_READY_INITIAL)
				.semantics(DeliverySemantics.ONCE)
				.createdAt(System.currentTimeMillis())
				.updatedAt(System.currentTimeMillis())
				.build());
	}

	private @Nullable Identity resolveIdentity(@NotNull ScenarioContext context) {
		UUID connectionUniqueId = context.getConnectionUniqueId();
		if (connectionUniqueId != null)
			return identityService.findByConnectionUniqueId(connectionUniqueId).orElse(null);

		UUID accountUniqueId = context.getAccountUniqueId();
		return accountUniqueId != null ? identityService.findByAccountUniqueId(accountUniqueId).orElse(null) : null;
	}

	private @NotNull String resolveExpiredMessage(@NotNull PipelineType pipelineType) {
		Messages.Scenarios.Scenario scenario = resolveScenarioMessages(pipelineType);
		List<String> lines = scenario.getPipelineExpired();
		if (lines.isEmpty()) return "";
		return String.join("\n", lines);
	}

	private @NotNull Messages.Scenarios.Scenario resolveScenarioMessages(@NotNull PipelineType type) {
		Messages.Scenarios scenarios = messagesProvider.get().getScenarios();

		if (type == PipelineType.REGISTRATION) return scenarios.getRegistration();
		if (type == PipelineType.MIGRATION) return scenarios.getMigration();

		return scenarios.getAuthentication();
	}

	private @Nullable PipelineType resolvePipelineType(@NotNull ScenarioContext context) {
        return switch (context) {
            case AuthContext ignored -> PipelineType.AUTHENTICATION;
            case RegistrationContext ignored -> PipelineType.REGISTRATION;
            case MigrationContext ignored -> PipelineType.MIGRATION;
            default -> null;
        };

    }

	private @NotNull JobKey jobKey(@NotNull PipelineType type, @NotNull ScenarioRequiredEvent event) {
		String correlation = "pending:" + type.name() +
				"|c=" + event.getConnectionUniqueId() +
				"|i=" + event.getAccountUniqueId();

		return JobKey.of(ORIGIN, PURPOSE, correlation);
	}

	private @NotNull JobKey jobKey(@NotNull PipelineType type, @NotNull ScenarioResolvedEvent event) {
		String correlation = "pending:" + type.name() +
				"|c=" + event.getConnectionUniqueId() +
				"|i=" + event.getAccountUniqueId();

		return JobKey.of(ORIGIN, PURPOSE, correlation);
	}

	private void emitResolved(@NotNull ScenarioContext context) {
		switch (context) {
			case AuthContext authContext -> eventManager.call(new AuthenticationResolvedEvent(
					authContext,
					ScenarioResolution.EXPIRED,
					false
			));
			case RegistrationContext registrationContext -> eventManager.call(new RegistrationResolvedEvent(
					registrationContext,
					ScenarioResolution.EXPIRED,
					false
			));
			case MigrationContext migrationContext -> eventManager.call(new MigrationResolvedEvent(
					migrationContext,
					ScenarioResolution.EXPIRED,
					false
			));
			default -> {
			}
		}
	}
}
