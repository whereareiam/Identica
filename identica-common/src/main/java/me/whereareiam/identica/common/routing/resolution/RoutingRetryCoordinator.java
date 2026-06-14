package me.whereareiam.identica.common.routing.resolution;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptFinishedEvent;
import me.whereareiam.identica.event.routing.intent.*;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.model.scheduler.DelayedRunnableTask;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.RoutingIntentStatus;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Singleton
public class RoutingRetryCoordinator implements EventListener {
	private static final Origin ORIGIN = Origin.core(RoutingRetryCoordinator.class);
	private static final Purpose PURPOSE = Purpose.of("routing-retry");

	private final RoutingAttemptService routingAttemptService;
	private final Scheduler scheduler;
	private final EventManager eventManager;

	@Inject
	public RoutingRetryCoordinator(
			@NotNull RoutingAttemptService routingAttemptService,
			Scheduler scheduler,
			@NotNull EventManager eventManager
	) {
		this.routingAttemptService = routingAttemptService;
		this.scheduler = scheduler;
		this.eventManager = eventManager;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onRoutingIntentStarted(@NotNull RoutingIntentStartedEvent event) {
		cancel(event.getIntent().getConnectionUniqueId());
	}

	@IdenticEvent
	public void onRoutingIntentUpdated(@NotNull RoutingIntentUpdatedEvent event) {
		cancel(event.getIntent().getConnectionUniqueId());
	}

	@IdenticEvent
	public void onRoutingAttemptFinished(@NotNull RoutingAttemptFinishedEvent event) {
		RoutingAttemptTrigger trigger = event.getTrigger();
		if (trigger != RoutingAttemptTrigger.ASYNC_CONNECT && trigger != RoutingAttemptTrigger.SCHEDULED_RETRY) return;
		if (event.getReport().isAccepted()) return;
		if (!isRetryable(event)) return;

		RoutingIntent intent = event.getIntent();
		if (intent.getStatus() != RoutingIntentStatus.PENDING) return;

		long delayMs = retryDelayMillis(intent);
		Logger.debug("Routing retry scheduled connection=%s target=%s delayMs=%s trigger=%s attempts=%s",
				intent.getConnectionUniqueId(),
				intent.getEndpoint().getServer(),
				delayMs,
				trigger,
				intent.getAttemptState().getAttempts());
		scheduler.schedule(DelayedRunnableTask.builder()
				.key(jobKey(intent.getConnectionUniqueId()))
				.delay(delayMs)
				.runnable(() -> retry(intent.getConnectionUniqueId(), intent.getId()))
				.build());
	}

	@IdenticEvent
	public void onRoutingIntentReached(@NotNull RoutingIntentReachedEvent event) {
		cancel(event.getIntent().getConnectionUniqueId());
	}

	@IdenticEvent
	public void onRoutingIntentExhausted(@NotNull RoutingIntentExhaustedEvent event) {
		cancel(event.getIntent().getConnectionUniqueId());
	}

	@IdenticEvent
	public void onRoutingIntentCleared(@NotNull RoutingIntentClearedEvent event) {
		cancel(event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onShutdown(@NotNull IdenticaShutdownEvent event) {
		scheduler.cancelByOrigin(ORIGIN);
	}

	private void retry(@NotNull UUID connectionUniqueId, @NotNull UUID intentUniqueId) {
		RoutingIntent intent = routingAttemptService.current(connectionUniqueId).orElse(null);
		if (intent == null) return;
		if (!intent.getId().equals(intentUniqueId)) return;
		if (intent.getStatus() != RoutingIntentStatus.PENDING) return;

		Logger.debug("Routing retry requested connection=%s target=%s attempts=%s",
				connectionUniqueId,
				intent.getEndpoint().getServer(),
				intent.getAttemptState().getAttempts());
		eventManager.call(new RoutingIntentRetryEvent(intent));
	}

	private void cancel(@NotNull UUID connectionUniqueId) {
		scheduler.cancel(jobKey(connectionUniqueId));
	}

	private long retryDelayMillis(@NotNull RoutingIntent intent) {
        return Math.max(0L, intent.getAttemptPolicy().getRetryDelay().toMillis());
	}

	private boolean isRetryable(@NotNull RoutingAttemptFinishedEvent event) {
		RoutingAttemptFailure failure = event.getReport().getFailure();
		RoutingAttemptFailureReason failureReason = failure != null ? failure.getReason() : null;
		if (failureReason == null) return true;
		return failureReason.isRetryable();
	}

	private @NotNull JobKey jobKey(@NotNull UUID connectionUniqueId) {
		return JobKey.of(ORIGIN, PURPOSE, connectionUniqueId.toString());
	}
}
