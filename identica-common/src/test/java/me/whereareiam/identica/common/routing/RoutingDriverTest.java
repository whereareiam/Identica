package me.whereareiam.identica.common.routing;

import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.*;
import me.whereareiam.identica.model.routing.execution.RoutingOutcome;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Routing Execution Coordinator")
class RoutingDriverTest {
	@DisplayName("Records normalized failed async routing outcomes")
	@Test
	void recordsNormalizedFailedAsyncRoutingOutcomes() {
		RoutingAttemptService routingAttemptService = mock(RoutingAttemptService.class);
		PlatformRoutingAdapter platformRoutingAdapter = mock(PlatformRoutingAdapter.class);
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		new RoutingDriver(routingAttemptService, platformRoutingAdapter, eventController);

		RoutingIntent intent = intent();
		RoutingConnectionSnapshot snapshot = new RoutingConnectionSnapshot(intent.getConnectionUniqueId(), "whereareiam", "auth");

		when(platformRoutingAdapter.snapshot(intent.getConnectionUniqueId())).thenReturn(Optional.of(snapshot));
		when(routingAttemptService.decide(any())).thenReturn(RoutingAttemptDecision.allowed(intent));
		when(platformRoutingAdapter.route(any())).thenReturn(CompletableFuture.completedFuture(
				RoutingOutcome.failed(intent.getEndpoint().getServer(), new RoutingAttemptFailure(
						RoutingAttemptFailureReason.SERVER_DISCONNECTED,
						"You are not whitelisted on this server!"
				))
		));

		eventController.call(new RoutingIntentStartedEvent(intent));

		ArgumentCaptor<RoutingAttemptReport> reportCaptor = ArgumentCaptor.forClass(RoutingAttemptReport.class);
		verify(routingAttemptService).record(reportCaptor.capture());

		RoutingAttemptReport report = reportCaptor.getValue();
		RoutingAttemptFailure failure = report.getFailure();
		assertEquals(RoutingAttemptFailureReason.SERVER_DISCONNECTED, failure.getReason());
		assertEquals("You are not whitelisted on this server!", failure.getDetail());
	}

	@DisplayName("Retry events preserve the scheduled retry trigger")
	@Test
	void retryEventsPreserveTheScheduledRetryTrigger() {
		RoutingAttemptService routingAttemptService = mock(RoutingAttemptService.class);
		PlatformRoutingAdapter platformRoutingAdapter = mock(PlatformRoutingAdapter.class);
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		new RoutingDriver(routingAttemptService, platformRoutingAdapter, eventController);

		RoutingIntent intent = intent();
		RoutingConnectionSnapshot snapshot = new RoutingConnectionSnapshot(intent.getConnectionUniqueId(), "whereareiam", "auth");

		when(platformRoutingAdapter.snapshot(intent.getConnectionUniqueId())).thenReturn(Optional.of(snapshot));
		when(routingAttemptService.decide(any())).thenReturn(RoutingAttemptDecision.allowed(intent));
		when(platformRoutingAdapter.route(any())).thenReturn(CompletableFuture.completedFuture(
				RoutingOutcome.accepted(intent.getEndpoint().getServer())
		));

		eventController.call(new RoutingIntentRetryEvent(intent));

		ArgumentCaptor<RoutingAttemptReport> reportCaptor = ArgumentCaptor.forClass(RoutingAttemptReport.class);
		verify(routingAttemptService).record(reportCaptor.capture());

		RoutingAttemptReport report = reportCaptor.getValue();
		assertEquals(RoutingAttemptTrigger.SCHEDULED_RETRY, report.getTrigger());
		assertNull(report.getFailure());
	}

	private RoutingIntent intent() {
		return new RoutingIntent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				new RoutingEndpoint("lobby"),
				RoutingReason.STEP,
				new RoutingAttemptPolicy(),
				new RoutingAttemptState(),
				PipelineType.AUTHENTICATION,
				null,
				null,
				null,
				System.currentTimeMillis()
		);
	}
}
