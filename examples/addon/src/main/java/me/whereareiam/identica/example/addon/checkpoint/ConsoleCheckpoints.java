package me.whereareiam.identica.example.addon.checkpoint;

import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * The example's two checkpoints, which the console answers for: players who join through a {@code hold.} hostname
 * wait on the loading screen where the proxy can hold them there, and on the step's server otherwise; players who
 * join through an {@code ask.} hostname always wait on the step's server, in its world.
 */
public final class ConsoleCheckpoints {
	private final List<ConsoleCheckpoint> checkpoints;

	private ConsoleCheckpoints(List<ConsoleCheckpoint> checkpoints) {
		this.checkpoints = checkpoints;
	}

	/**
	 * Registers the example's checkpoints and the listeners that tell the console what they ask.
	 *
	 * @param identica Identica's API
	 * @param console  where the checkpoints tell what they hold and ask
	 * @return the registered checkpoints
	 */
	public static ConsoleCheckpoints register(IdenticaAPI identica, Consumer<String> console) {
		List<ConsoleCheckpoint> checkpoints = List.of(
				new ConsoleCheckpoint("hold.", List.of(CheckpointPlacement.LOADING_SCREEN, CheckpointPlacement.STEP_SERVER), console),
				new ConsoleCheckpoint("ask.", List.of(CheckpointPlacement.STEP_SERVER), console)
		);
		checkpoints.forEach(checkpoint -> {
			identica.getCheckpoints().register(checkpoint);
			identica.getEventManager().register(checkpoint);
		});

		return new ConsoleCheckpoints(checkpoints);
	}

	/**
	 * Answers the waiting step of a held player through the provider's own command, such as
	 * {@code answer("Alice", "login Secret-123")}.
	 *
	 * @param username the held player
	 * @param command  the command to run as the player, without its slash
	 * @return whether a checkpoint holds a player of that name, and the command ran
	 */
	public Optional<CompletionStage<Boolean>> answer(String username, String command) {
		return checkpoints.stream()
				.map(checkpoint -> checkpoint.answer(username, command))
				.flatMap(Optional::stream)
				.findFirst();
	}
}
