package me.whereareiam.identica.example.addon.bungeecord;

import me.whereareiam.identica.example.addon.checkpoint.ConsoleCheckpoints;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Arrays;

/**
 * {@code /examplehold <player> <command>} on BungeeCord: the console answers the waiting step of a player the
 * example checkpoints hold, such as {@code /examplehold Alice login Secret-123}.
 */
final class BungeeCordHoldCommand extends Command {
	private final ConsoleCheckpoints checkpoints;

	BungeeCordHoldCommand(ConsoleCheckpoints checkpoints) {
		super("examplehold");
		this.checkpoints = checkpoints;
	}

	@Override
	public void execute(CommandSender sender, String[] arguments) {
		if (sender instanceof ProxiedPlayer) return;
		if (arguments.length < 2) {
			sender.sendMessage(new TextComponent("Usage: /examplehold <player> <command>"));
			return;
		}

		String player = arguments[0];
		String command = String.join(" ", Arrays.copyOfRange(arguments, 1, arguments.length));
		checkpoints.answer(player, command).ifPresentOrElse(
				answered -> answered.thenAccept(ran -> sender.sendMessage(new TextComponent("Example checkpoint answered for " + player + ": " + ran))),
				() -> sender.sendMessage(new TextComponent("Example checkpoint holds no " + player))
		);
	}
}
