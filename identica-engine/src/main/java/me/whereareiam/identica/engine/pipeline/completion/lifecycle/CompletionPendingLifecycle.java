package me.whereareiam.identica.engine.pipeline.completion.lifecycle;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.completion.runtime.CompletionPipeline;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.pipeline.attempt.PipelineAttemptFinishedEvent;
import me.whereareiam.identica.event.routing.completion.CompletionRoutingReachedEvent;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Routing;
import me.whereareiam.identica.model.delivery.DeliveryDispatchContext;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.completion.CompletionPendingState;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CompletionPendingLifecycle implements EventListener {
	private final DeliveryService deliveryService;
	private final CompletionPipeline completionPipeline;
	private final IdentityService identityService;
	private final Provider<Routing> routingProvider;
	private final Map<UUID, String> joinedServerByConnection = new ConcurrentHashMap<>();

	@Inject
	public CompletionPendingLifecycle(
			@NotNull DeliveryService deliveryService,
			@NotNull CompletionPipeline completionPipeline,
			@NotNull IdentityService identityService,
			@NotNull Provider<Routing> routingProvider,
			@NotNull EventManager eventManager
	) {
		this.deliveryService = deliveryService;
		this.completionPipeline = completionPipeline;
		this.identityService = identityService;
		this.routingProvider = routingProvider;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onSessionOpened(@NotNull SessionOpenedEvent event) {
		String completionTarget = resolveCompletionTarget(event.getPipelineType());
		deliveryService.queue(DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.COMPLETION)
				.target(DeliveryTarget.builder()
						.connectionUniqueId(event.getConnectionUniqueId())
						.accountUniqueId(event.getSession().getUniqueId())
						.build())
				.payload(DeliveryPayload.builder()
						.completion(DeliveryPayload.CompletionPayload.builder()
								.connectionUniqueId(event.getConnectionUniqueId())
								.accountUniqueId(event.getSession().getUniqueId())
								.pipelineType(event.getPipelineType())
								.build())
						.build())
				.semantics(DeliverySemantics.ONCE)
				.requiredServer(completionTarget)
				.createdAt(System.currentTimeMillis())
				.updatedAt(System.currentTimeMillis())
				.build());
	}

	@IdenticEvent
	public void onServerJoined(@NotNull ServerJoinedEvent event) {
		UUID connectionUniqueId = event.getIdentity().getConnectionUniqueId();
		if (connectionUniqueId != null) joinedServerByConnection.put(connectionUniqueId, event.getServer());

		dispatchCompletion(event.getIdentity(), event.getServer());
	}

	@IdenticEvent
	public void onPipelineAttemptFinished(@NotNull PipelineAttemptFinishedEvent event) {
		PipelineResult result = event.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE) return;
		if (!isBlank(resolveCompletionTarget(event.getPipelineType()))) return;

		UUID connectionUniqueId = event.getContext() != null ? event.getContext().getConnectionUniqueId() : null;
		if (connectionUniqueId == null) return;

		// Without a completion target the player joins no further server, so a player already in a server's world
		// would otherwise never receive the completion.
		String joinedServer = joinedServerByConnection.get(connectionUniqueId);
		if (joinedServer == null) return;

		identityService.findByConnectionUniqueId(connectionUniqueId).ifPresent(identity -> {
			Logger.debug("Completion dispatching on current server connection=%s pipeline=%s server=%s",
					connectionUniqueId, event.getPipelineType(), joinedServer);
			dispatchCompletion(identity, joinedServer);
		});
	}

	@IdenticEvent
	public void onIdentityDetached(@NotNull IdentityDetachedEvent event) {
		joinedServerByConnection.remove(event.getUniqueId());
	}

	@IdenticEvent
	public void onCompletionRoutingReached(@NotNull CompletionRoutingReachedEvent event) {
		UUID connectionUniqueId = event.getIntent().getConnectionUniqueId();
		String currentServer = event.getCurrentServer();
		// Routing reaches a target the player already joined without a new join, such as the server it is on.
		if (currentServer == null || !currentServer.equalsIgnoreCase(joinedServerByConnection.get(connectionUniqueId)))
			return;

		identityService.findByConnectionUniqueId(connectionUniqueId)
				.ifPresent(identity -> dispatchCompletion(identity, currentServer));
	}

	private void dispatchCompletion(@NotNull Identity identity, @NotNull String server) {
		List<DeliveryRequest> delivered = deliveryService.dispatch(DeliveryDispatchContext.builder()
				.identity(identity)
				.server(server)
				.build());
		for (DeliveryRequest request : delivered) {
			DeliveryPayload.CompletionPayload completion = request.getPayload().getCompletion();
			if (completion == null) continue;

			completionPipeline.complete(identity, CompletionPendingState.builder()
					.pipelineType(completion.getPipelineType())
					.connectionUniqueId(completion.getConnectionUniqueId())
					.accountUniqueId(completion.getAccountUniqueId())
					.build());
			deliveryService.acknowledge(request.getId(), "completion-dispatched");
		}
	}

	private String resolveCompletionTarget(@NotNull PipelineType pipelineType) {
		Routing routing = routingProvider.get();
		Routing.Target target = routing.getDefaults().getComplete();
		Routing.Targets scenarioTargets = resolveScenarioTargets(routing, pipelineType);
		Routing.Target scenarioTarget = scenarioTargets != null ? scenarioTargets.getComplete() : null;
		if (scenarioTarget != null && !isBlank(scenarioTarget.getTarget()))
			return scenarioTarget.getTarget();

		return target.getTarget();
	}

	private Routing.Targets resolveScenarioTargets(
			@NotNull Routing routing,
			@NotNull PipelineType pipelineType
	) {
		String scenarioId = pipelineType.name().toLowerCase(Locale.ROOT);
		for (Map.Entry<String, Routing.Targets> entry : routing.getScenarios().entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			if (entry.getKey().equalsIgnoreCase(scenarioId))
				return entry.getValue();
		}

		return null;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
