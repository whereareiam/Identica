package me.whereareiam.identica.common.routing.resolution.failure;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptFinishedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import org.jetbrains.annotations.NotNull;

@Singleton
public class RoutingMissingTargetHandler extends AbstractRoutingFailureHandler implements EventListener {
	@Inject
	public RoutingMissingTargetHandler(
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
	public void onRoutingAttemptFinished(@NotNull RoutingAttemptFinishedEvent event) {
		RoutingAttemptReport report = event.getReport();
		RoutingAttemptFailure failure = report.getFailure();
		if (report.isAccepted()) return;
		if (failure == null || failure.getReason() != RoutingAttemptFailureReason.MISSING_SERVER) return;

		Logger.warn("Routing missing target connection=%s target=%s",
				event.getIntent().getConnectionUniqueId(),
				event.getIntent().getEndpoint().getServer());
		disconnect(event.getIntent(), failure.getReason(), failure.getDetail());
	}
}
