package me.whereareiam.identica.common.messaging;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.delivery.DeliveryDispatchContext;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.service.DeliveryStore;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultDeliveryService implements DeliveryService {
	private final DeliveryStore deliveryStore;
	private final PipelineStateStore pipelineStateStore;

	@Override
	public void queue(@NotNull DeliveryRequest request) {
		if (request.getTarget().isEmpty()) return;
		if (request.getPayload().isEmpty()) return;

		deliveryStore.put(request);
	}

	@Override
	public @NotNull List<DeliveryRequest> pendingForConnection(@NotNull UUID connectionUniqueId) {
		return deliveryStore.findByConnectionUniqueId(connectionUniqueId);
	}

	@Override
	public @NotNull List<DeliveryRequest> pendingForAccount(@NotNull UUID accountUniqueId) {
		return deliveryStore.findByAccountUniqueId(accountUniqueId);
	}

	@Override
	public @NotNull List<DeliveryRequest> dispatch(@NotNull DeliveryDispatchContext context) {
		Identity identity = context.getIdentity();
		Map<UUID, DeliveryRequest> requests = new LinkedHashMap<>();

		UUID connectionUniqueId = identity.getConnectionUniqueId();
		if (connectionUniqueId != null) {
			for (DeliveryRequest request : pendingForConnection(connectionUniqueId))
				requests.put(request.getId(), request);
		}

		UUID accountUniqueId = identity.getAccountUniqueId();
		if (accountUniqueId != null) {
			for (DeliveryRequest request : pendingForAccount(accountUniqueId))
				requests.put(request.getId(), request);
		}

		List<DeliveryRequest> dispatched = new ArrayList<>();
		for (DeliveryRequest request : requests.values()) {
			if (request.getCheckpoint() != context.getCheckpoint()) continue;
			if (!matchesRequiredServer(request, context)) continue;
			if (!isStillValid(request, identity)) {
				acknowledge(request.getId(), "invalidated");
				continue;
			}

			deliver(identity, request);
			dispatched.add(request);

			if (request.getSemantics() == DeliverySemantics.ONCE && request.getPayload().getCompletion() == null)
				acknowledge(request.getId(), "delivered-once");
		}

		return List.copyOf(dispatched);
	}

	@Override
	public void acknowledge(@NotNull UUID deliveryUniqueId, @NotNull String reason) {
		DeliveryRequest request = deliveryStore.findById(deliveryUniqueId).orElse(null);
		if (request == null) return;

		deliveryStore.remove(deliveryUniqueId);
		Logger.debug("Delivery acknowledged id=%s source=%s reason=%s",
				deliveryUniqueId, request.getSource(), reason);
	}

	@Override
	public void invalidateByConnection(@NotNull UUID connectionUniqueId, @NotNull String reason) {
		for (DeliveryRequest request : pendingForConnection(connectionUniqueId))
			acknowledge(request.getId(), reason);
	}

	@Override
	public void invalidateByAccount(@NotNull UUID accountUniqueId, @NotNull String reason) {
		for (DeliveryRequest request : pendingForAccount(accountUniqueId))
			acknowledge(request.getId(), reason);
	}

	@Override
	public @NotNull Optional<DeliveryRequest> find(@NotNull UUID deliveryUniqueId) {
		return deliveryStore.findById(deliveryUniqueId);
	}

	private void deliver(@NotNull Identity identity, @NotNull DeliveryRequest request) {
		DeliveryPayload payload = request.getPayload();
		if (payload.getCompletion() != null)
			return;

		if (payload.getTitle() != null && !payload.getTitle().isBlank()) {
			identity.sendTitle(Title.title(
					Serializer.serialize(identity, payload.getTitle()),
					Serializer.serialize(identity, payload.getSubtitle() == null ? "" : payload.getSubtitle()),
					Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
			));
		}

		if (payload.getChatMessage() != null && !payload.getChatMessage().isBlank()) {
			Component message = Serializer.serialize(identity, payload.getChatMessage());
			identity.sendMessage(message);
		}

		if (payload.getActionBarMessage() != null && !payload.getActionBarMessage().isBlank())
			identity.getAudience().sendActionBar(Serializer.serialize(identity, payload.getActionBarMessage()));
	}

	private boolean matchesRequiredServer(
			@NotNull DeliveryRequest request,
			@NotNull DeliveryDispatchContext context
	) {
		String targetServer = request.getRequiredServer();
		if (targetServer == null || targetServer.isBlank()) return true;
		if (context.getCurrentServer() == null || context.getCurrentServer().isBlank()) return false;

		return targetServer.equalsIgnoreCase(context.getCurrentServer());
	}

	private boolean isStillValid(@NotNull DeliveryRequest request, @NotNull Identity identity) {
		if (request.getSource() != DeliverySource.INITIAL_PROMPT) return true;
		if (request.getMarker() == null) return true;

		PipelineStateReference reference = PipelineStateReference.from(identity);
		if (reference.isEmpty()) return false;

		PipelineState state = pipelineStateStore.find(reference).orElse(null);
		if (state == null) return false;

		JourneyStateItem journey = state.get(JourneyStateItem.KEY).orElse(null);
		String stageId = journey != null ? journey.getStageId() : null;
		int stepIndex = journey != null ? journey.getStepIndex() : -1;

		return state.getPipelineType() == request.getMarker().getPipelineType()
				&& stepIndex == request.getMarker().getStepIndex()
				&& java.util.Objects.equals(stageId, request.getMarker().getStageId());
	}
}
