package me.whereareiam.identica.common.routing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptFinishedEvent;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptStartedEvent;
import me.whereareiam.identica.event.routing.completion.CompletionRoutingReachedEvent;
import me.whereareiam.identica.event.routing.completion.CompletionRoutingStartedEvent;
import me.whereareiam.identica.event.routing.intent.*;
import me.whereareiam.identica.event.routing.step.StepRoutingReachedEvent;
import me.whereareiam.identica.event.routing.step.StepRoutingStartedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.RoutingPlan;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptDecision;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptRequest;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.routing.RoutingIntentStore;
import me.whereareiam.identica.type.routing.RoutingIntentStatus;
import me.whereareiam.identica.type.routing.RoutingPlanAction;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultRoutingCoordinator implements RoutingCoordinator, RoutingAttemptService {
	private final RoutingPlanner routingPlanner;
	private final RoutingIntentStore routingIntentStore;
	private final EventManager eventManager;

	@Override
	public void accept(@NotNull RoutingSignal signal) {
		Logger.debug("Routing signal accepted type=%s pipeline=%s connection=%s stage=%s step=%s",
				signal.getType(),
				signal.getPipelineType(),
				signal.connectionUniqueId(),
				signal.getStage() != null ? signal.getStage().id() : null,
				signal.getStep() != null ? signal.getStep().getName() : null);
		apply(routingPlanner.plan(signal));
	}

	@Override
	public void markReached(@NotNull UUID connectionUniqueId, @NotNull String serverName) {
		RoutingIntent intent = routingIntentStore.markReached(connectionUniqueId, serverName).orElse(null);
		if (intent == null) {
			Logger.debug("Routing reached ignored connection=%s server=%s reason=no-matching-intent",
					connectionUniqueId, serverName);
			return;
		}

		Logger.debug("Routing reached connection=%s target=%s reason=%s attempts=%s",
				connectionUniqueId,
				serverName,
				intent.getReason(),
				intent.getAttemptState().getAttempts());
		publishReached(intent, serverName);
		if (intent.getAttemptPolicy().isConsumeOnReached())
			clear(connectionUniqueId);
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		RoutingIntent intent = routingIntentStore.consume(connectionUniqueId).orElse(null);
		if (intent != null) {
			intent.setStatus(RoutingIntentStatus.CLEARED);
			intent.setUpdatedAt(System.currentTimeMillis());
		}
		Logger.debug("Routing intent cleared connection=%s hadIntent=%s", connectionUniqueId, intent != null);
		eventManager.call(new RoutingIntentClearedEvent(connectionUniqueId, intent));
	}

	@Override
	public @NotNull RoutingAttemptDecision decide(@NotNull RoutingAttemptRequest request) {
		RoutingIntent intent = routingIntentStore.peek(request.getConnectionUniqueId()).orElse(null);
		if (intent == null) {
			Logger.debug("Routing attempt skipped connection=%s trigger=%s reason=no-intent current=%s",
					request.getConnectionUniqueId(), request.getTrigger(), request.getCurrentServer());
			return RoutingAttemptDecision.skipped("no-intent");
		}
		if (intent.getStatus() != RoutingIntentStatus.PENDING) {
			Logger.debug("Routing attempt skipped connection=%s trigger=%s reason=intent-not-pending status=%s target=%s current=%s",
					request.getConnectionUniqueId(), request.getTrigger(), intent.getStatus(), intent.getEndpoint().getServer(), request.getCurrentServer());
			return RoutingAttemptDecision.skipped("intent-not-pending");
		}

		String currentServer = request.getCurrentServer();
		if (currentServer != null && currentServer.equalsIgnoreCase(intent.getEndpoint().getServer())) {
			Logger.debug("Routing attempt skipped connection=%s trigger=%s reason=already-reached target=%s",
					request.getConnectionUniqueId(), request.getTrigger(), intent.getEndpoint().getServer());
			markReached(request.getConnectionUniqueId(), currentServer);
			return RoutingAttemptDecision.skipped("already-reached");
		}

		if (!intent.getAttemptPolicy().allowsAttempt(intent.getAttemptState().getAttempts())) {
			Logger.debug("Routing attempt exhausted connection=%s trigger=%s target=%s attempts=%s mode=%s",
					request.getConnectionUniqueId(),
					request.getTrigger(),
					intent.getEndpoint().getServer(),
					intent.getAttemptState().getAttempts(),
					intent.getAttemptPolicy().getMode());
			exhaust(intent);
			return RoutingAttemptDecision.exhausted(intent);
		}

		Logger.debug("Routing attempt allowed connection=%s trigger=%s target=%s current=%s attempts=%s mode=%s",
				request.getConnectionUniqueId(),
				request.getTrigger(),
				intent.getEndpoint().getServer(),
				request.getCurrentServer(),
				intent.getAttemptState().getAttempts(),
				intent.getAttemptPolicy().getMode());
		eventManager.call(new RoutingAttemptStartedEvent(intent, request));
		return RoutingAttemptDecision.allowed(intent);
	}

	@Override
	public void record(@NotNull RoutingAttemptReport report) {
		RoutingIntent intent = routingIntentStore.recordAttempt(report).orElse(null);
		if (intent == null) {
			Logger.debug("Routing attempt report ignored connection=%s trigger=%s accepted=%s server=%s reason=no-intent",
					report.getConnectionUniqueId(), report.getTrigger(), report.isAccepted(), report.getServer());
			return;
		}

		Logger.debug("Routing attempt recorded connection=%s trigger=%s accepted=%s server=%s attempts=%s failureReason=%s",
				report.getConnectionUniqueId(),
				report.getTrigger(),
				report.isAccepted(),
				report.getServer(),
				intent.getAttemptState().getAttempts(),
				report.getFailure() != null ? report.getFailure().getReason() : null);
		eventManager.call(new RoutingAttemptFinishedEvent(intent, report));
	}

	@Override
	public @NotNull Optional<RoutingIntent> current(@NotNull UUID connectionUniqueId) {
		return routingIntentStore.peek(connectionUniqueId);
	}

	private void apply(@NotNull RoutingPlan plan) {
		RoutingPlanAction action = plan.getAction();
		if (action == RoutingPlanAction.IGNORE) {
			Logger.debug("Routing plan ignored");
			return;
		}

		if (action == RoutingPlanAction.CLEAR) {
			Logger.debug("Routing plan clearing connection=%s", plan.getConnectionUniqueId());
			if (plan.getConnectionUniqueId() != null)
				clear(plan.getConnectionUniqueId());
			return;
		}

		RoutingIntent intent = plan.getIntent();
		if (intent == null) return;

		RoutingIntent previous = routingIntentStore.consume(intent.getConnectionUniqueId()).orElse(null);
		if (previous != null) {
			Logger.debug("Routing intent replaced connection=%s oldTarget=%s newTarget=%s reason=%s",
					intent.getConnectionUniqueId(),
					previous.getEndpoint().getServer(),
					intent.getEndpoint().getServer(),
					intent.getReason());
			eventManager.call(new RoutingIntentClearedEvent(intent.getConnectionUniqueId(), previous));
		}

		routingIntentStore.put(intent);
		Logger.debug("Routing intent stored connection=%s target=%s reason=%s provider=%s step=%s policy=%s",
				intent.getConnectionUniqueId(),
				intent.getEndpoint().getServer(),
				intent.getReason(),
				intent.getProviderId(),
				intent.getStepName(),
				intent.getAttemptPolicy().getMode());
		if (action == RoutingPlanAction.START || previous == null) {
			publishStarted(intent);
			return;
		}

		eventManager.call(new RoutingIntentUpdatedEvent(intent));
	}

	private void exhaust(@NotNull RoutingIntent intent) {
		routingIntentStore.markExhausted(intent.getConnectionUniqueId());
		eventManager.call(new RoutingIntentExhaustedEvent(intent));
		if (intent.getAttemptPolicy().isConsumeOnExhausted())
			clear(intent.getConnectionUniqueId());
	}

	private void publishStarted(@NotNull RoutingIntent intent) {
		eventManager.call(new RoutingIntentStartedEvent(intent));
		if (intent.getReason() == RoutingReason.STEP)
			eventManager.call(new StepRoutingStartedEvent(intent));
		if (intent.getReason() == RoutingReason.COMPLETION)
			eventManager.call(new CompletionRoutingStartedEvent(intent));
	}

	private void publishReached(@NotNull RoutingIntent intent, @NotNull String currentServer) {
		eventManager.call(new RoutingIntentReachedEvent(intent, currentServer));
		if (intent.getReason() == RoutingReason.STEP)
			eventManager.call(new StepRoutingReachedEvent(intent, currentServer));
		if (intent.getReason() == RoutingReason.COMPLETION)
			eventManager.call(new CompletionRoutingReachedEvent(intent, currentServer));
	}
}
