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
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentClearedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentStartedEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentUpdatedEvent;
import me.whereareiam.identica.event.scenario.ScenarioResolvedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.checkpoint.CheckpointSelection;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.platform.adapter.PlatformCheckpointAdapter;
import me.whereareiam.identica.routing.RoutingAttemptService;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import me.whereareiam.identica.type.routing.RoutingIntentStatus;
import me.whereareiam.identica.type.routing.reason.RoutingClearReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Picks a checkpoint for each waiting step and ends the holds of connections whose journey no longer waits.
 *
 * <p>A checkpoint holds a connection at the first of its placements the platform can hold for it. On the loading
 * screen, the platform holds the connection and hands it over through {@link #arrive(HeldConnection)}; Identica then
 * continues the journey. On the step's server, the hold starts once the player is in the world of a server and
 * routing moves them nowhere else.</p>
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
	private final Provider<RoutingAttemptService> attempts;
	private final List<Checkpoint> registered = new CopyOnWriteArrayList<>();
	private final Map<UUID, CheckpointSelection> selections = new ConcurrentHashMap<>();
	private final Map<UUID, CheckpointRequest> waiting = new ConcurrentHashMap<>();
	private final Map<UUID, HeldConnection> held = new ConcurrentHashMap<>();
	private final Set<UUID> handedOver = ConcurrentHashMap.newKeySet();
	private final Map<UUID, String> joined = new ConcurrentHashMap<>();

	@Inject
	public DefaultCheckpoints(
			@NotNull PlatformCheckpointAdapter platform,
			@NotNull Provider<RoutingCoordinator> routing,
			@NotNull Provider<RoutingAttemptService> attempts,
			@NotNull EventManager eventManager
	) {
		this.platform = platform;
		this.routing = routing;
		this.attempts = attempts;
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
	public @NotNull Optional<CheckpointSelection> select(@NotNull CheckpointRequest request) {
		UUID connectionUniqueId = request.getConnectionUniqueId();
		waiting.put(connectionUniqueId, request);
		if (held.containsKey(connectionUniqueId)) return Optional.ofNullable(selections.get(connectionUniqueId));
		if (handedOver.contains(connectionUniqueId)) return Optional.empty();

		Set<CheckpointPlacement> available = EnumSet.of(CheckpointPlacement.STEP_SERVER);
		available.addAll(platform.placements(connectionUniqueId));
		CheckpointSelection selected = registered.stream()
				.map(checkpoint -> selection(checkpoint, available, request))
				.flatMap(Optional::stream)
				.findFirst()
				.orElse(null);
		Logger.debug("Checkpoint selected connection=%s step=%s prompt=%s checkpoint=%s placement=%s",
				connectionUniqueId, request.getStep().getName(), request.getResult().getPrompt(),
				selected != null ? selected.getCheckpoint().id() : "step-server",
				selected != null ? selected.getPlacement() : null);
		if (selected == null) {
			selections.remove(connectionUniqueId);
			return Optional.empty();
		}

		selections.put(connectionUniqueId, selected);
		return Optional.of(selected);
	}

	@Override
	public @NotNull Optional<CheckpointSelection> selected(@NotNull UUID connectionUniqueId) {
		return Optional.ofNullable(selections.get(connectionUniqueId));
	}

	@Override
	public boolean holds(@NotNull UUID connectionUniqueId) {
		return held.containsKey(connectionUniqueId);
	}

	@Override
	public void arrive(@NotNull HeldConnection connection) {
		UUID connectionUniqueId = connection.getConnectionUniqueId();
		CheckpointSelection selection = selections.get(connectionUniqueId);
		if (selection == null || selection.getPlacement() != CheckpointPlacement.LOADING_SCREEN) {
			connection.release(null);
			return;
		}

		if (!hold(connection, selection)) return;

		connection.resume().whenComplete((decision, error) -> {
			if (error != null) Logger.warn(error, "Checkpoint could not continue the journey connection=%s", connectionUniqueId);
		});
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
		UUID connectionUniqueId = event.getUniqueId();
		forget(connectionUniqueId);
		joined.remove(connectionUniqueId);
		HeldConnection connection = held.remove(connectionUniqueId);
		if (connection instanceof StepServerHold hold) hold.release(null);
	}

	/**
	 * Starts the step-server hold of a connection whose client entered the world of the server routing sent it to.
	 */
	@IdenticEvent
	public void onServerJoined(@NotNull ServerJoinedEvent event) {
		UUID connectionUniqueId = event.getIdentity().getConnectionUniqueId();
		joined.put(connectionUniqueId, event.getServer());
		holdOnServer(connectionUniqueId);
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
		if (event.getReason() == RoutingClearReason.REPLACED) return;
		if (selections.containsKey(connectionUniqueId)) {
			holdOnServer(connectionUniqueId);
			return;
		}

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

	private @NotNull Optional<CheckpointSelection> selection(
			@NotNull Checkpoint checkpoint,
			@NotNull Set<CheckpointPlacement> available,
			@NotNull CheckpointRequest request
	) {
		CheckpointPlacement placement = checkpoint.placements().stream()
				.filter(available::contains)
				.findFirst()
				.orElse(null);
		if (placement == null || !checkpoint.accepts(request)) return Optional.empty();

		return Optional.of(new CheckpointSelection(checkpoint, placement));
	}

	/**
	 * Holds a connection whose checkpoint asks on the step's server, once its client is in a server's world and
	 * routing moves it nowhere else.
	 */
	private void holdOnServer(@NotNull UUID connectionUniqueId) {
		CheckpointSelection selection = selections.get(connectionUniqueId);
		String server = joined.get(connectionUniqueId);
		if (selection == null || selection.getPlacement() != CheckpointPlacement.STEP_SERVER || server == null) return;

		RoutingIntent intent = attempts.get().current(connectionUniqueId).orElse(null);
		if (intent != null && intent.getStatus() == RoutingIntentStatus.PENDING && !server.equalsIgnoreCase(intent.getEndpoint().getServer()))
			return;

		hold(new StepServerHold(connectionUniqueId), selection);
	}

	private boolean hold(@NotNull HeldConnection connection, @NotNull CheckpointSelection selection) {
		UUID connectionUniqueId = connection.getConnectionUniqueId();
		if (held.putIfAbsent(connectionUniqueId, connection) != null) return false;

		connection.released().whenComplete((ignored, error) -> held.remove(connectionUniqueId, connection));
		Logger.debug("Checkpoint holds connection=%s checkpoint=%s placement=%s",
				connectionUniqueId, selection.getCheckpoint().id(), selection.getPlacement());
		selection.getCheckpoint().arrived(new Stop(connection, selection.getPlacement()));

		return true;
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
	 * A connection held on the step's server. The platform resumed its journey when the player reached the server,
	 * so the hold only ends: the player stays where routing sends them.
	 */
	@RequiredArgsConstructor
	private static final class StepServerHold implements HeldConnection {
		private final @NotNull UUID connectionUniqueId;
		private final CompletableFuture<Void> released = new CompletableFuture<>();

		@Override
		public @NotNull UUID getConnectionUniqueId() {
			return connectionUniqueId;
		}

		@Override
		public @NotNull CompletionStage<ConnectionDecision> resume() {
			return CompletableFuture.failedFuture(new IllegalStateException("The journey resumed on the step's server"));
		}

		@Override
		public void release(@Nullable String destination) {
			released.complete(null);
		}

		@Override
		public void handOver(@Nullable String destination) {
			released.complete(null);
		}

		@Override
		public @NotNull CompletionStage<Void> released() {
			return released;
		}
	}

	/**
	 * The held connection as its checkpoint sees it.
	 */
	@RequiredArgsConstructor
	private final class Stop implements CheckpointStop {
		private final @NotNull HeldConnection connection;
		private final @NotNull CheckpointPlacement placement;

		@Override
		public @NotNull UUID getConnectionUniqueId() {
			return connection.getConnectionUniqueId();
		}

		@Override
		public @NotNull CheckpointPlacement getPlacement() {
			return placement;
		}

		@Override
		public @NotNull Optional<CheckpointRequest> getWaiting() {
			return Optional.ofNullable(waiting.get(connection.getConnectionUniqueId()));
		}

		@Override
		public @NotNull CompletionStage<Boolean> answer(@NotNull String command) {
			return platform.answer(connection.getConnectionUniqueId(), command);
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
