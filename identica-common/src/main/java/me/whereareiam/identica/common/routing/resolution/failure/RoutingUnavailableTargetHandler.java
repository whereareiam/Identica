package me.whereareiam.identica.common.routing.resolution.failure;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentExhaustedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class RoutingUnavailableTargetHandler extends AbstractRoutingFailureHandler implements EventListener {
	@Inject
	public RoutingUnavailableTargetHandler(
			@NotNull Provider<Messages> messagesProvider,
			@NotNull PipelineStateStore pipelineStateStore,
			@NotNull IdentityService identityService,
			@NotNull SessionService sessionService,
			@NotNull PlatformRoutingAdapter platformRoutingAdapter,
			@NotNull EventManager eventManager
	) {
		super(messagesProvider, pipelineStateStore, identityService, sessionService, platformRoutingAdapter);
		eventManager.register(this);
	}

	@IdenticEvent
	public void onRoutingIntentExhausted(@NotNull RoutingIntentExhaustedEvent event) {
		RoutingIntent intent = event.getIntent();
		RoutingAttemptFailure failure = intent.getAttemptState().getLastFailure();
		RoutingAttemptFailureReason failureReason = failure != null ? failure.getReason() : null;
		if (!isDisconnectOnExhausted(failureReason)) return;

		Logger.warn("Routing unavailable target exhausted connection=%s target=%s reason=%s detail=%s",
				intent.getConnectionUniqueId(),
				intent.getEndpoint().getServer(),
				failureReason,
				failure != null ? failure.getDetail() : null);
		disconnect(intent, failureReason, failure != null ? failure.getDetail() : null);
	}

	private boolean isDisconnectOnExhausted(@Nullable RoutingAttemptFailureReason failureReason) {
		if (failureReason == null) return false;
		return failureReason == RoutingAttemptFailureReason.SERVER_DISCONNECTED
				|| failureReason == RoutingAttemptFailureReason.CONNECTION_EXCEPTION
				|| failureReason == RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING;
	}
}
