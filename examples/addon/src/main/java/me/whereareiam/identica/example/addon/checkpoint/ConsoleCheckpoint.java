package me.whereareiam.identica.example.addon.checkpoint;

import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.step.StepFinishedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import me.whereareiam.identica.type.pipeline.journey.step.StepWaitReason;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Holds players who join through a hostname with a given prefix at a checkpoint and tells the proxy's console what
 * their steps ask for; {@link ConsoleCheckpoints#answer} answers them. An addon that shows prompts itself, such as
 * dialogs, does the same with its own way of asking the player. It uses only Identica's API, so it works on every
 * proxy.
 */
public final class ConsoleCheckpoint implements Checkpoint, EventListener {
	private final String hostnamePrefix;
	private final List<CheckpointPlacement> placements;
	private final Consumer<String> console;
	private final Map<UUID, CheckpointStop> stops = new ConcurrentHashMap<>();
	private final Map<UUID, String> names = new ConcurrentHashMap<>();

	/**
	 * @param hostnamePrefix the start of the hostname players join through to be held here, such as {@code hold.}
	 * @param placements     where the checkpoint asks, most wanted first
	 * @param console        where the checkpoint tells what it holds and asks
	 */
	public ConsoleCheckpoint(String hostnamePrefix, List<CheckpointPlacement> placements, Consumer<String> console) {
		this.hostnamePrefix = hostnamePrefix;
		this.placements = placements;
		this.console = console;
	}

	@Override
	public @NotNull String id() {
		return "example-" + hostnamePrefix.replace(".", "");
	}

	@Override
	public @NotNull List<CheckpointPlacement> placements() {
		return placements;
	}

	/**
	 * Takes the connections that joined through a hostname with the checkpoint's prefix.
	 */
	@Override
	public boolean accepts(@NotNull CheckpointRequest request) {
		ConnectionIdentity.Origin origin = request.getContext().getIdentity().getOrigin();
		return origin != null && origin.getHost().toLowerCase(Locale.ROOT).startsWith(hostnamePrefix);
	}

	@Override
	public void arrived(@NotNull CheckpointStop stop) {
		UUID connection = stop.getConnectionUniqueId();
		String name = stop.getWaiting()
				.map(waiting -> waiting.getContext().getIdentity().getUsername())
				.orElse(connection.toString());
		stops.put(connection, stop);
		names.put(connection, name);
		stop.released().whenComplete((ignored, error) -> {
			stops.remove(connection, stop);
			names.remove(connection, name);
			console.accept("Example checkpoint released " + name);
		});
		console.accept("Example checkpoint holds " + name + " on the " + stop.getPlacement().name().toLowerCase(Locale.ROOT).replace('_', ' '));
		stop.getWaiting()
				.filter(waiting -> waiting.getResult().getWaitReason() != StepWaitReason.ONLINE)
				.ifPresent(waiting -> asks(name, waiting.getStep().getName(), waiting.getResult().getMessage()));
	}

	/**
	 * Tells the console what a held player is asked, and Identica's answer to a wrong attempt.
	 */
	@IdenticEvent
	public void onStepFinished(StepFinishedEvent event) {
		UUID connection = event.getContext().getConnectionUniqueId();
		if (connection == null || !stops.containsKey(connection)) return;
		if (event.getResult().getStatus() != StepResult.StepStatus.WAITING || event.getResult().getWaitReason() == StepWaitReason.ONLINE) return;

		asks(names.getOrDefault(connection, connection.toString()), event.getStep().getName(), event.getResult().getMessage());
	}

	/**
	 * Answers the waiting step of a player this checkpoint holds through the provider's own command.
	 *
	 * @param username the held player
	 * @param command  the command to run as the player, without its slash
	 * @return whether this checkpoint holds a player of that name, and the command ran
	 */
	Optional<CompletionStage<Boolean>> answer(String username, String command) {
		return names.entrySet().stream()
				.filter(entry -> entry.getValue().equalsIgnoreCase(username))
				.map(entry -> stops.get(entry.getKey()))
				.filter(stop -> stop != null)
				.findFirst()
				.map(stop -> stop.answer(command));
	}

	private void asks(String name, String step, String message) {
		console.accept("Example checkpoint asks " + name + " for " + step + (message == null || message.isBlank() ? "" : ": " + message));
	}
}
