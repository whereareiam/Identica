package me.whereareiam.identica.platform.common.decision;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.delivery.DeliveryMarker;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConnectionDecisionDeliveryCoordinator {
	private final @NotNull DeliveryService deliveryService;
	private final @NotNull PipelineStateStore pipelineStateStore;

	public boolean queueWaitDecision(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable ConnectionDecision decision,
			@NotNull DeliveryCheckpoint checkpoint
	) {
		if (decision == null || decision.getStatus() != ConnectionDecision.Status.WAIT) return false;

		String message = decision.getMessage();
		if (message == null || message.isBlank()) return false;

		PipelineStateReference reference = PipelineStateReference.builder()
				.connectionUniqueId(connectionUniqueId)
				.build();
		PipelineState state = pipelineStateStore.find(reference).orElse(null);
		JourneyStateItem journey = state != null ? state.item(JourneyStateItem.class).orElse(null) : null;

		deliveryService.queue(DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.INITIAL_PROMPT)
				.target(DeliveryTarget.builder()
						.connectionUniqueId(connectionUniqueId)
						.accountUniqueId(accountUniqueId)
						.build())
				.payload(DeliveryPayload.builder()
						.chatMessage(message)
						.build())
				.checkpoint(checkpoint)
				.semantics(DeliverySemantics.ONCE)
				.marker(DeliveryMarker.builder()
						.pipelineType(state != null ? state.getPipelineType() : null)
						.stageId(journey != null ? journey.getStageId() : null)
						.stepIndex(journey != null ? journey.getStepIndex() : -1)
						.build())
				.createdAt(System.currentTimeMillis())
				.updatedAt(System.currentTimeMillis())
				.build());

		return true;
	}
}
