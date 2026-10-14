package me.whereareiam.identica.platform.common.decision;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
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

	/**
	 * Queues the prompt of a waiting decision for the connection, marked with the journey position it belongs to.
	 *
	 * @param connection connection the decision is for
	 * @param decision decision of the connection's pipeline
	 * @param checkpoint when the prompt is delivered
	 * @return {@code true} when the prompt was queued
	 */
	public boolean queueWaitDecision(
			@NotNull ConnectionIdentity connection,
			@Nullable ConnectionDecision decision,
			@NotNull DeliveryCheckpoint checkpoint
	) {
		if (decision == null || decision.getStatus() != ConnectionDecision.Status.WAIT) return false;

		String message = decision.getMessage();
		if (message == null || message.isBlank()) return false;

		UUID connectionUniqueId = connection.getConnectionUniqueId();
		if (connectionUniqueId == null) return false;

		PipelineState state = pipelineStateStore.find(PipelineStateReference.from(connection)).orElse(null);
		JourneyStateItem journey = state != null ? state.get(JourneyStateItem.KEY).orElse(null) : null;

		deliveryService.queue(DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.INITIAL_PROMPT)
				.target(DeliveryTarget.builder()
						.connectionUniqueId(connectionUniqueId)
						.accountUniqueId(connection.getAccountUniqueId())
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
