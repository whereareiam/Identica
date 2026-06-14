package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("Pending Scenario Account Lifecycle")
class PendingScenarioAccountLifecycleTest {
	@DisplayName("Connection termination consumes pending scenario state and emits cancelled resolution")
	@Test
	void connectionTerminationConsumesPendingScenarioStateAndEmitsCancelledResolution() {
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		IdentityService identityService = mock(IdentityService.class);
		DeliveryService deliveryService = mock(DeliveryService.class);
		EventController eventController = new EventController();
		Capture capture = new Capture();
		eventController.register(capture);
		new PendingScenarioAccountLifecycle(pipelineStateStore, identityService, this::messages, deliveryService, eventController);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		AuthContext context = AuthContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new me.whereareiam.identica.identity.actor.ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.AUTHENTICATION);
		state.setScenario(context);
		state.putItem(new JourneyStateItem(null, null, 0), 1_000L);

		when(pipelineStateStore.consume(any(PipelineStateReference.class))).thenReturn(Optional.of(state));

		eventController.call(new ConnectionTerminatedEvent(connectionUniqueId, accountUniqueId, PipelineType.AUTHENTICATION));

		assertEquals(ScenarioResolution.CANCELLED, capture.authenticationResolved.get().getReason());
	}

	@DisplayName("Pending migration termination queues the cancelled notice and emits cancelled resolution")
	@Test
	void pendingMigrationTerminationQueuesCancelledNoticeAndEmitsCancelledResolution() {
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		IdentityService identityService = mock(IdentityService.class);
		DeliveryService deliveryService = mock(DeliveryService.class);
		EventController eventController = new EventController();
		Capture capture = new Capture();
		eventController.register(capture);
		new PendingScenarioAccountLifecycle(pipelineStateStore, identityService, this::messages, deliveryService, eventController);

		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		MigrationContext context = MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new me.whereareiam.identica.identity.actor.ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build();
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.MIGRATION);
		state.setScenario(context);
		state.putItem(new MigrationPendingState("premium", System.currentTimeMillis(), MigrationInitiator.ADMIN, null), 1_000L);
		state.putItem(new JourneyStateItem(null, null, 0), 1_000L);

		when(pipelineStateStore.consume(any(PipelineStateReference.class))).thenReturn(Optional.of(state));

		eventController.call(new ConnectionTerminatedEvent(connectionUniqueId, accountUniqueId, PipelineType.MIGRATION));

		verify(deliveryService).queue(argThat(this::matchesCancelledNotice));
		assertEquals(ScenarioResolution.CANCELLED, capture.migrationResolved.get().getReason());
	}

	private boolean matchesCancelledNotice(DeliveryRequest request) {
		return request.getTarget() != null
				&& request.getTarget().getAccountUniqueId() != null
				&& "cancelled".equals(request.getPayload().getChatMessage());
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Scenarios scenarios = new Messages.Scenarios();
		Messages.Scenarios.Migration migration = new Messages.Scenarios.Migration();
		migration.setCancelled(List.of("cancelled"));
		scenarios.setMigration(migration);
		messages.setScenarios(scenarios);
		return messages;
	}

	private static final class Capture implements EventListener {
		private final AtomicReference<AuthenticationResolvedEvent> authenticationResolved = new AtomicReference<>();
		private final AtomicReference<MigrationResolvedEvent> migrationResolved = new AtomicReference<>();

		@IdenticEvent
		public void onAuthenticationResolved(AuthenticationResolvedEvent event) {
			authenticationResolved.set(event);
		}

		@IdenticEvent
		public void onMigrationResolved(MigrationResolvedEvent event) {
			migrationResolved.set(event);
		}
	}
}
