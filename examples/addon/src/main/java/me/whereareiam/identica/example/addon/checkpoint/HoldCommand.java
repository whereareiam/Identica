package me.whereareiam.identica.example.addon.checkpoint;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;

import java.util.Arrays;

/**
 * {@code /examplehold <player> <command>} on Velocity: the console answers the waiting step of a player the example
 * checkpoints hold, such as {@code /examplehold Alice login Secret-123}.
 */
public final class HoldCommand implements SimpleCommand {
	private final ConsoleCheckpoints checkpoints;

	public HoldCommand(ConsoleCheckpoints checkpoints) {
		this.checkpoints = checkpoints;
	}

	@Override
	public void execute(Invocation invocation) {
		String[] arguments = invocation.arguments();
		if (arguments.length < 2) {
			invocation.source().sendMessage(Component.text("Usage: /examplehold <player> <command>"));
			return;
		}

		String player = arguments[0];
		String command = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));
		checkpoints.answer(player, command).ifPresentOrElse(
				answered -> answered.thenAccept(ran -> invocation.source()
						.sendMessage(Component.text("Example checkpoint answered for " + player + ": " + ran))),
				() -> invocation.source().sendMessage(Component.text("Example checkpoint holds no " + player))
		);
	}

	@Override
	public boolean hasPermission(Invocation invocation) {
		return invocation.source() instanceof ConsoleCommandSource;
	}
}
