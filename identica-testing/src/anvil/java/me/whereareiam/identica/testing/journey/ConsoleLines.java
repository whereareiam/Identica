package me.whereareiam.identica.testing.journey;

import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.process.ProcessConsole;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The lines a proxy prints from one moment on, as plain text, kept so they can be searched again from any
 * position. BungeeCord colours what it prints with ANSI escape codes, which split a coloured message into pieces;
 * Velocity prints plain text.
 */
final class ConsoleLines {
	private static final Pattern ESCAPE = Pattern.compile("\u001B\\[[0-9;]*m");
	private static final int BATCH = 500;

	private final ConsoleWatch watch;
	private final List<String> lines = new ArrayList<>();

	private ConsoleLines(ConsoleWatch watch) {
		this.watch = watch;
	}

	/**
	 * Follows the lines a console prints from now on.
	 */
	static ConsoleLines from(ProcessConsole console) {
		return new ConsoleLines(console.watch());
	}

	/**
	 * Follows a console from the oldest line it still keeps, such as what the proxy printed while it started.
	 */
	static ConsoleLines fromStart(ProcessConsole console) {
		return new ConsoleLines(console.watchFromStart());
	}

	/**
	 * Returns every line printed since this transcript began, including those printed since the previous call.
	 *
	 * @throws IllegalStateException when the console dropped lines this transcript had not read yet
	 */
	List<String> lines() {
		while (true) {
			ConsoleOutput output = watch.read(BATCH);
			if (output.isTruncated())
				throw new IllegalStateException("The console dropped lines before they were read");

			output.getLines().forEach(line -> lines.add(plain(line.getText())));
			if (output.getLines().size() < BATCH) return List.copyOf(lines);
		}
	}

	/**
	 * Returns the lines printed after a position of this transcript.
	 *
	 * @param position number of lines that came before, as {@link #lines()} counted them
	 */
	List<String> after(int position) {
		List<String> all = lines();
		return all.subList(Math.min(position, all.size()), all.size());
	}

	/**
	 * Returns a console line without its colours.
	 */
	static String plain(String line) {
		return ESCAPE.matcher(line).replaceAll("");
	}
}
