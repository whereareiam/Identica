package me.whereareiam.identica.common.routing.resolution;

import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptFinishedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentReachedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentRetryEvent;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.*;
import me.whereareiam.identica.model.scheduler.*;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Routing Retry Coordinator")
class RoutingRetryCoordinatorTest {
	@DisplayName("Schedules a retry event after a failed async routing attempt")
	@Test
	void failedAsyncAttemptSchedulesRetryEvent() {
		TestScheduler scheduler = new TestScheduler();
		TestRoutingAttemptService routingAttemptService = new TestRoutingAttemptService();
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		RetryCapture capture = new RetryCapture();
		eventController.register(capture);

		RoutingIntent intent = intent();
		routingAttemptService.currentIntent = intent;
		new RoutingRetryCoordinator(routingAttemptService, scheduler, eventController);

		eventController.call(new RoutingAttemptFinishedEvent(
				intent,
				RoutingAttemptReport.failed(
						intent.getConnectionUniqueId(),
						RoutingAttemptTrigger.ASYNC_CONNECT,
						intent.getEndpoint().getServer(),
						RoutingAttemptFailureReason.SERVER_DISCONNECTED
				)
		));

		assertEquals(1, scheduler.tasks.size());
		DelayedRunnableTask task = scheduler.onlyTask();
		assertNotNull(task);
		assertEquals(1_000L, task.getDelay());

		task.getRunnable().run();

		assertEquals(1, capture.retries.get());
	}

	@DisplayName("Does not schedule retries for non-retryable routing failures")
	@Test
	void missingServerFailureIsNotRetried() {
		TestScheduler scheduler = new TestScheduler();
		TestRoutingAttemptService routingAttemptService = new TestRoutingAttemptService();
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();

		RoutingIntent intent = intent();
		routingAttemptService.currentIntent = intent;
		new RoutingRetryCoordinator(routingAttemptService, scheduler, eventController);

		eventController.call(new RoutingAttemptFinishedEvent(
				intent,
				RoutingAttemptReport.failed(
						intent.getConnectionUniqueId(),
						RoutingAttemptTrigger.ASYNC_CONNECT,
						intent.getEndpoint().getServer(),
						RoutingAttemptFailureReason.MISSING_SERVER
				)
		));

		assertTrue(scheduler.tasks.isEmpty());
	}

	@DisplayName("Cancels pending retries when the routing intent is reached")
	@Test
	void reachedIntentCancelsPendingRetry() {
		TestScheduler scheduler = new TestScheduler();
		TestRoutingAttemptService routingAttemptService = new TestRoutingAttemptService();
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();

		RoutingIntent intent = intent();
		routingAttemptService.currentIntent = intent;
		new RoutingRetryCoordinator(routingAttemptService, scheduler, eventController);

		eventController.call(new RoutingAttemptFinishedEvent(
				intent,
				RoutingAttemptReport.failed(
						intent.getConnectionUniqueId(),
						RoutingAttemptTrigger.ASYNC_CONNECT,
						intent.getEndpoint().getServer(),
						RoutingAttemptFailureReason.SERVER_DISCONNECTED
				)
		));
		assertFalse(scheduler.tasks.isEmpty());

		eventController.call(new RoutingIntentReachedEvent(intent, intent.getEndpoint().getServer()));

		assertTrue(scheduler.tasks.isEmpty());
	}

	private RoutingIntent intent() {
		RoutingAttemptPolicy policy = new RoutingAttemptPolicy();
		policy.setRetryDelay(Duration.ofSeconds(1));

		UUID connectionId = UUID.randomUUID();
		return new RoutingIntent(
				UUID.randomUUID(),
				connectionId,
				new RoutingEndpoint("lobby"),
				RoutingReason.STEP,
				policy,
				new RoutingAttemptState(),
				PipelineType.AUTHENTICATION,
				null,
				null,
				null,
				System.currentTimeMillis()
		);
	}

	private static final class RetryCapture implements EventListener {
		private final AtomicInteger retries = new AtomicInteger();

		@IdenticEvent
		public void onRetry(@SuppressWarnings("unused") RoutingIntentRetryEvent event) {
			retries.incrementAndGet();
		}
	}

	private static final class TestRoutingAttemptService implements RoutingAttemptService {
		private RoutingIntent currentIntent;

		@Override
		public @NotNull RoutingAttemptDecision decide(@NotNull RoutingAttemptRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void record(@NotNull RoutingAttemptReport report) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull Optional<RoutingIntent> current(@NotNull UUID connectionUniqueId) {
			if (currentIntent == null) return Optional.empty();
			if (!currentIntent.getConnectionUniqueId().equals(connectionUniqueId)) return Optional.empty();
			return Optional.of(currentIntent);
		}
	}

	private static final class TestScheduler implements Scheduler {
		private final Map<JobKey, DelayedRunnableTask> tasks = new HashMap<>();

		@Override
		public void schedule(RunnableTask runnableTask) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask) {
			tasks.put(runnableTask.getKey(), runnableTask);
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void schedule(RunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void cancel(JobKey key) {
			tasks.remove(key);
		}

		@Override
		public void cancelByOrigin(Origin origin) {
			tasks.entrySet().removeIf(entry -> entry.getKey().getOrigin().equals(origin));
		}

		private DelayedRunnableTask onlyTask() {
			return tasks.values().stream().findFirst().orElse(null);
		}
	}
}
