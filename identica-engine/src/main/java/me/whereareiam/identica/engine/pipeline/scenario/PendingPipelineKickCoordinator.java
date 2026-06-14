package me.whereareiam.identica.engine.pipeline.scenario;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.event.scenario.ScenarioRequiredEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.model.scheduler.DelayedRunnableTask;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class PendingPipelineKickCoordinator implements EventListener {
	private static final Origin ORIGIN = Origin.core(PendingPipelineKickCoordinator.class);
	private static final Purpose PURPOSE = Purpose.of("pipeline-expiry");

	private final Provider<Messages> messagesProvider;
	private final IdentityService identityService;
	private final Scheduler scheduler;
	private final ConnectionLifecycleService connectionLifecycleService;

	@Inject
	public PendingPipelineKickCoordinator(
			Provider<Messages> messagesProvider,
			IdentityService identityService,
			Scheduler scheduler,
			EventManager eventManager,
			ConnectionLifecycleService connectionLifecycleService
	) {
		this.messagesProvider = messagesProvider;
		this.identityService = identityService;
		this.scheduler = scheduler;
		this.connectionLifecycleService = connectionLifecycleService;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onShutdown(@NotNull IdenticaShutdownEvent event) {
		scheduler.cancelByOrigin(ORIGIN);
	}

	@IdenticEvent
	public void onScenarioRequired(@NotNull ScenarioRequiredEvent event) {
		PipelineType type = resolvePipelineType(event.getContext());
		if (type == null) return;

		long delayMs = Math.max(0L, event.getExpiresAt() - System.currentTimeMillis());
		DelayedRunnableTask task = DelayedRunnableTask.builder()
				.key(jobKey(type, event))
				.delay(delayMs)
				.runnable(() -> disconnectExpired(type, event.getContext()))
				.build();

		scheduler.schedule(task);
	}

	@IdenticEvent
	public void onScenarioResolved(@NotNull ScenarioResolvedEvent event) {
		PipelineType type = resolvePipelineType(event.getContext());
		if (type == null) return;

		scheduler.cancel(jobKey(type, event));
	}

	private void disconnectExpired(@NotNull PipelineType type, @NotNull ScenarioContext context) {
		Identity identity = resolveIdentity(context);
		connectionLifecycleService.terminated(
				context.getConnectionUniqueId(),
				context.getAccountUniqueId(),
				type
		);
		if (identity == null) return;

		identity.disconnect(Serializer.serialize(
				identity,
				Serializer.template("{message}")
						.section("message", section -> section
								.lines(resolveScenarioMessages(type).getPipelineExpired()))
						.render()
		));
	}

	private @Nullable Identity resolveIdentity(@NotNull ScenarioContext context) {
		UUID connectionUniqueId = context.getConnectionUniqueId();
		if (connectionUniqueId != null)
			return identityService.findByConnectionUniqueId(connectionUniqueId).orElse(null);

		UUID accountUniqueId = context.getAccountUniqueId();
		return accountUniqueId != null
				? identityService.findByAccountUniqueId(accountUniqueId).orElse(null)
				: null;
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
}
