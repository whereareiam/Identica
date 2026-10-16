package me.whereareiam.identica.testing.journey;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessConsole;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a proxy's console as plain text. BungeeCord colours what it prints with ANSI escape codes, which split a
 * coloured message into pieces; Velocity prints plain text.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConsoleLines {
	private static final Pattern ESCAPE = Pattern.compile("\u001B\\[[0-9;]*m");

	/**
	 * Returns the console lines after a checkpoint as plain text.
	 *
	 * @param console proxy console
	 * @param after checkpoint the lines follow
	 * @param limit most lines to read
	 */
	static List<String> read(ProcessConsole console, long after, int limit) {
		return console.read(after, limit).getLines().stream().map(line -> plain(line.getText())).toList();
	}

	/**
	 * Returns a console line without its colours.
	 */
	static String plain(String line) {
		return ESCAPE.matcher(line).replaceAll("");
	}
}
