package me.whereareiam.identica.common.checkpoint;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.checkpoint.Checkpoints;
import me.whereareiam.identica.checkpoint.HeldConnection;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentClearedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.routing.reason.RoutingClearReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Picks a checkpoint for each waiting step and ends the holds of connections whose journey no longer waits.
 *
 * <p>A connection keeps the checkpoint that holds it for every later step of its journey. A finished journey routes
 * its connection to where it completes right after it resolves; the hold ends with that destination, so the platform
 * sends the player straight there. A cancelled journey, or routing that drops its intent, ends the hold where the
 * player is. An expired, denied or failed journey disconnects the player, which ends the hold without letting the
 * player reach a server first. A connection handed over to the step's server waits there for the rest of its
 * journey.</p>
 */
@Singleton
public class DefaultCheckpoints implements Checkpoints, EventListener {
	private final PlatformCheckpointAdapter platform;
	private final Provider<RoutingCoordinator> routing;
	private final List<Checkpoint> registered = new CopyOnWriteArrayList<>();
	private final Map<UUID, Checkpoint> selections = new ConcurrentHashMap<>();
	private final Map<UUID, CheckpointRequest> waiting = new ConcurrentHashMap<>();
	private final Map<UUID, HeldConnection> held = new ConcurrentHashMap<>();
	private final Set<UUID> handedOver = ConcurrentHashMap.newKeySet();

	@Inject
	public DefaultCheckpoints(
			@NotNull PlatformCheckpointAdapter platform,
			@NotNull Provider<RoutingCoordinator> routing,
			@NotNull EventManager eventManager
	) {
		this.platform = platform;
		this.routing = routing;
		eventManager.register(this);
	}

	@Override
	public void register(@NotNull Checkpoint checkpoint) {
		registered.addFirst(checkpoint);
	}

	@Override
	public void unregister(@NotNull Checkpoint checkpoint) {
		registered.remove(checkpoint);
	}

	@Override
	public @NotNull Optional<Checkpoint> select(@NotNull CheckpointRequest request) {
		UUID connectionUniqueId = request.getConnectionUniqueId();
		waiting.put(connectionUniqueId, request);
		if (held.containsKey(connectionUniqueId)) return Optional.ofNullable(selections.get(connectionUniqueId));
		if (handedOver.contains(connectionUniqueId)) return Optional.empty();

		Checkpoint selected = registered.stream()
				.filter(checkpoint -> platform.placements().contains(checkpoint.placement()))
				.filter(checkpoint -> checkpoint.accepts(request))
				.findFirst()
				.orElse(null);
		Logger.debug("Checkpoint selected connection=%s step=%s reason=%s checkpoint=%s",
				connectionUniqueId, request.getStep().getName(), request.getResult().getWaitReason(),
				selected != null ? selected.id() : "step-server");
		if (selected == null) {
			selections.remove(connectionUniqueId);
			return Optional.empty();
		}

		selections.put(connectionUniqueId, selected);
		return Optional.of(selected);
	}

	@Override
	public @NotNull Optional<Checkpoint> selected(@NotNull UUID connectionUniqueId) {
		return Optional.ofNullable(selections.get(connectionUniqueId));
	}

	@Override
	public boolean holds(@NotNull UUID connectionUniqueId) {
		return held.containsKey(connectionUniqueId);
	}

	@Override
	public void arrive(@NotNull HeldConnection connection) {
		UUID connectionUniqueId = connection.getConnectionUniqueId();
		Checkpoint checkpoint = selections.get(connectionUniqueId);
		if (checkpoint == null) {
			connection.release(null);
			return;
		}

		held.put(connectionUniqueId, connection);
		connection.released().whenComplete((ignored, error) -> held.remove(connectionUniqueId, connection));
		Logger.debug("Checkpoint holds connection=%s checkpoint=%s", connectionUniqueId, checkpoint.id());
		checkpoint.arrived(new Stop(connection));
	}

	/**
	 * Forgets the checkpoint of a journey that left the waiting state, and ends the hold of a cancelled one.
	 */
	@IdenticEvent
	public void onScenarioResolved(@NotNull ScenarioResolvedEvent event) {
		UUID connectionUniqueId = event.getConnectionUniqueId();
		forget(connectionUniqueId);
		if (event.getReason() == ScenarioResolution.CANCELLED) release(connectionUniqueId, null);
	}

	/**
	 * Forgets a connection that left; the platform ends its hold itself.
	 */
	@IdenticEvent
	public void onIdentityDetached(@NotNull IdentityDetachedEvent event) {
		forget(event.getUniqueId());
		held.remove(event.getUniqueId());
	}

	@IdenticEvent
	public void onRoutingStarted(@NotNull RoutingIntentStartedEvent event) {
		routed(event.getIntent());
	}

	@IdenticEvent
	public void onRoutingUpdated(@NotNull RoutingIntentUpdatedEvent event) {
		routed(event.getIntent());
	}

	/**
	 * Ends the hold of a connection whose journey routes it nowhere, once nothing of it waits anymore.
	 */
	@IdenticEvent
	public void onRoutingCleared(@NotNull RoutingIntentClearedEvent event) {
		UUID connectionUniqueId = event.getConnectionUniqueId();
		if (event.getReason() == RoutingClearReason.REPLACED || selections.containsKey(connectionUniqueId)) return;

		release(connectionUniqueId, null);
	}

	/**
	 * Ends a hold once routing sends the connection somewhere: to where its journey completes, or to the step's
	 * server of a connection that was handed over.
	 */
	private void routed(@NotNull RoutingIntent intent) {
		UUID connectionUniqueId = intent.getConnectionUniqueId();
		if (intent.getReason() == RoutingReason.COMPLETION || handedOver.contains(connectionUniqueId))
			release(connectionUniqueId, intent.getEndpoint().getServer());
	}

	/**
	 * Hands a held connection over to the step's server: routing plans the waiting step again, now without a
	 * checkpoint, which ends the hold with the step's server as destination.
	 */
	private void handOver(@NotNull HeldConnection connection) {
		UUID connectionUniqueId = connection.getConnectionUniqueId();
		CheckpointRequest request = waiting.get(connectionUniqueId);
		if (held.get(connectionUniqueId) != connection || request == null) return;

		Logger.debug("Checkpoint hands connection=%s over to the step's server step=%s", connectionUniqueId, request.getStep().getName());
		handedOver.add(connectionUniqueId);
		selections.remove(connectionUniqueId);
		routing.get().accept(RoutingSignal.stepFinished(
				request.getContext(),
				request.getPipelineType(),
				request.getStage(),
				request.getStep(),
				request.getResult()
		));
		release(connectionUniqueId, null);
	}

	private void release(@NotNull UUID connectionUniqueId, @Nullable String destination) {
		HeldConnection connection = held.remove(connectionUniqueId);
		if (connection == null) return;

		Logger.debug("Checkpoint releases connection=%s destination=%s", connectionUniqueId, destination);
		if (handedOver.contains(connectionUniqueId)) {
			connection.handOver(destination);
			return;
		}

		connection.release(destination);
	}

	private void forget(@NotNull UUID connectionUniqueId) {
		selections.remove(connectionUniqueId);
		waiting.remove(connectionUniqueId);
		handedOver.remove(connectionUniqueId);
	}

	/**
	 * The held connection as its checkpoint sees it.
	 */
	@RequiredArgsConstructor
	private final class Stop implements CheckpointStop {
		private final @NotNull HeldConnection connection;

		@Override
		public @NotNull UUID getConnectionUniqueId() {
			return connection.getConnectionUniqueId();
		}

		@Override
		public @NotNull CompletionStage<ConnectionDecision> resume() {
			return connection.resume();
		}

		@Override
		public @NotNull CompletionStage<Boolean> answer(@NotNull String command) {
			return connection.answer(command);
		}

		@Override
		public void handOver() {
			DefaultCheckpoints.this.handOver(connection);
		}

		@Override
		public @NotNull CompletionStage<Void> released() {
			return connection.released();
		}
	}
}
