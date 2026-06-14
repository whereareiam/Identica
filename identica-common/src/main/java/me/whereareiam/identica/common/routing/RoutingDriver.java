package me.whereareiam.identica.common.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.model.routing.execution.RoutingOutcome;
import me.whereareiam.identica.model.routing.execution.RoutingRequest;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;

@Singleton
public class RoutingDriver implements EventListener {
	private final RoutingAttemptService routingAttemptService;
	private final PlatformRoutingAdapter platformRoutingAdapter;

	@Inject
	public RoutingDriver(
			@NotNull RoutingAttemptService routingAttemptService,
			@NotNull PlatformRoutingAdapter platformRoutingAdapter,
			@NotNull EventManager eventManager
	) {
		this.routingAttemptService = routingAttemptService;
		this.platformRoutingAdapter = platformRoutingAdapter;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onRoutingIntentStarted(@NotNull RoutingIntentStartedEvent event) {
		execute(event.getIntent(), RoutingAttemptTrigger.ASYNC_CONNECT, "started");
	}

	@IdenticEvent
	public void onRoutingIntentUpdated(@NotNull RoutingIntentUpdatedEvent event) {
		execute(event.getIntent(), RoutingAttemptTrigger.ASYNC_CONNECT, "updated");
	}

	@IdenticEvent
	public void onRoutingIntentRetry(@NotNull RoutingIntentRetryEvent event) {
		execute(event.getIntent(), RoutingAttemptTrigger.SCHEDULED_RETRY, "retry");
	}

	private void execute(
			@NotNull RoutingIntent intent,
			@NotNull RoutingAttemptTrigger trigger,
			@NotNull String source
	) {
		RoutingConnectionSnapshot snapshot = platformRoutingAdapter.snapshot(intent.getConnectionUniqueId()).orElse(null);
		if (snapshot == null) {
			Logger.debug("Routing execution %s skipped connection=%s target=%s reason=player-offline",
					source, intent.getConnectionUniqueId(), intent.getEndpoint().getServer());
			return;
		}
		if (snapshot.getCurrentServer() == null) {
			Logger.debug("Routing execution %s skipped connection=%s target=%s reason=no-current-server",
					source, intent.getConnectionUniqueId(), intent.getEndpoint().getServer());
			return;
		}

		RoutingAttemptDecision decision = routingAttemptService.decide(new RoutingAttemptRequest(
				snapshot.getConnectionUniqueId(),
				trigger,
				snapshot.getCurrentServer()
		));
		if (!decision.isAllowed() || decision.getIntent() == null) {
			Logger.debug("Routing execution %s skipped connection=%s current=%s target=%s reason=%s exhausted=%s",
					source,
					snapshot.getConnectionUniqueId(),
					snapshot.getCurrentServer(),
					intent.getEndpoint().getServer(),
					decision.getReason(),
					decision.isExhausted());
			return;
		}

		RoutingIntent currentIntent = decision.getIntent();
		platformRoutingAdapter.route(new RoutingRequest(currentIntent, snapshot, trigger))
				.whenComplete((outcome, throwable) -> finish(currentIntent, trigger, outcome, throwable));
	}

	private void finish(
			@NotNull RoutingIntent intent,
			@NotNull RoutingAttemptTrigger trigger,
			RoutingOutcome outcome,
			Throwable throwable
	) {
		RoutingAttemptReport report;
		if (throwable != null) {
			Logger.debug("Routing execution failed connection=%s target=%s trigger=%s reason=%s",
					intent.getConnectionUniqueId(), intent.getEndpoint().getServer(), trigger, throwable.toString());
			report = RoutingAttemptReport.failed(
					intent.getConnectionUniqueId(),
					trigger,
					intent.getEndpoint().getServer(),
					RoutingAttemptFailureReason.CONNECTION_EXCEPTION,
					throwable.toString()
			);
			routingAttemptService.record(report);
			return;
		}
		if (outcome == null) {
			report = RoutingAttemptReport.failed(
					intent.getConnectionUniqueId(),
					trigger,
					intent.getEndpoint().getServer(),
					RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING
			);
			routingAttemptService.record(report);
			return;
		}

		report = outcome.isAccepted()
				? RoutingAttemptReport.succeeded(intent.getConnectionUniqueId(), trigger, outcome.getTargetServer())
				: RoutingAttemptReport.failed(
						intent.getConnectionUniqueId(),
						trigger,
						outcome.getTargetServer(),
						failureReason(outcome),
						failureDetail(outcome)
				);
		routingAttemptService.record(report);
	}

	private RoutingAttemptFailureReason failureReason(@NotNull RoutingOutcome outcome) {
		RoutingAttemptFailure failure = outcome.getFailure();
		return failure != null ? failure.getReason() : null;
	}

	private String failureDetail(@NotNull RoutingOutcome outcome) {
		RoutingAttemptFailure failure = outcome.getFailure();
		return failure != null ? failure.getDetail() : null;
	}
}
