package me.whereareiam.identica.testing.journey;

import me.whereareiam.identica.testing.environment.Platform;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The console lines a proxy platform writes about a player's connections, which a journey compares with what the
 * player's client sees. A report a platform never writes is {@code null}, and the journey then relies on the client
 * alone.
 */
enum ProxyReports {
	VELOCITY {
		@Override
		Predicate<String> accepted(String player, String source) {
			return line -> line.contains(connectedPlayer(player, source)) && line.contains("has connected");
		}

		@Override
		Predicate<String> disconnected(String player, String source) {
			return line -> line.contains(connectedPlayer(player, source)) && line.contains("has disconnected");
		}

		@Override
		Predicate<String> refused(String source) {
			return line -> line.contains("[initial connection] " + source) && line.contains("has disconnected");
		}

		@Override
		boolean namesLogins() {
			return true;
		}

		@Override
		boolean reasonFollows() {
			return true;
		}

		@Override
		@Nullable String connectedTo(String line, String player) {
			String connection = "[server connection] " + player + " -> ";
			int start = line.indexOf(connection);
			int end = line.indexOf(" has connected");
			if (start < 0 || end < start) return null;

			return line.substring(start + connection.length(), end);
		}

		@Override
		boolean disconnectedFrom(String line, String player, String backend) {
			return line.contains("[server connection] " + player + " -> " + backend + " has disconnected");
		}

		private String connectedPlayer(String player, String source) {
			return "[connected player] " + player + " (" + source;
		}
	},
	/**
	 * BungeeCord names a player only once the player reaches a backend; it writes nothing about a login it accepts
	 * or refuses, and the reason of a disconnect comes before the disconnect itself.
	 */
	BUNGEECORD {
		@Override
		@Nullable Predicate<String> accepted(String player, String source) {
			return null;
		}

		@Override
		Predicate<String> disconnected(String player, String source) {
			return line -> line.contains("[" + player + "] -> UpstreamBridge has disconnected");
		}

		@Override
		@Nullable Predicate<String> refused(String source) {
			return null;
		}

		@Override
		boolean namesLogins() {
			return false;
		}

		@Override
		boolean reasonFollows() {
			return false;
		}

		@Override
		@Nullable String connectedTo(String line, String player) {
			String connection = "[" + player + "] <-> ServerConnector [";
			int start = line.indexOf(connection);
			int end = line.indexOf("] has connected");
			if (start < 0 || end < start) return null;

			return line.substring(start + connection.length(), end);
		}

		@Override
		boolean disconnectedFrom(String line, String player, String backend) {
			return line.contains("[" + player + "] <-> ServerConnector [" + backend + "] has disconnected");
		}
	};

	private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");

	/**
	 * Returns the reports of the platform a proxy runs on.
	 */
	static ProxyReports of(Platform platform) {
		return switch (platform) {
			case VELOCITY -> VELOCITY;
			case BUNGEECORD -> BUNGEECORD;
		};
	}

	/**
	 * Matches the line about the proxy accepting a player's login.
	 *
	 * @param player username the proxy reports the player under
	 * @param source start of the address the player connects from, such as {@code /127.0.0.1:}, or blank
	 */
	abstract @Nullable Predicate<String> accepted(String player, String source);

	/**
	 * Matches the line about a player the proxy accepted leaving the proxy, for whatever reason.
	 */
	abstract Predicate<String> disconnected(String player, String source);

	/**
	 * Matches the line about a connection closing before the proxy accepted a login.
	 */
	abstract @Nullable Predicate<String> refused(String source);

	/**
	 * Returns whether the proxy names a player on its console as soon as it accepts the login, so it reports the
	 * end of every accepted connection, instead of only once the player reaches a backend.
	 */
	abstract boolean namesLogins();

	/**
	 * Returns whether the proxy writes the reason of a disconnect on the disconnect's line or after it, instead of
	 * before it.
	 */
	abstract boolean reasonFollows();

	/**
	 * Returns the backend a line reports a player connected to, or {@code null} for any other line.
	 */
	abstract @Nullable String connectedTo(String line, String player);

	/**
	 * Returns whether a line reports a player's connection to a backend closing, as when the backend refuses it.
	 */
	abstract boolean disconnectedFrom(String line, String player, String backend);

	/**
	 * Matches the line about a player connected to a backend.
	 */
	Predicate<String> connected(String player, String backend) {
		return line -> backend.equals(connectedTo(line, player));
	}

	/**
	 * Returns when the proxy wrote a line.
	 */
	LocalTime time(String line) {
		Matcher time = TIME.matcher(line);
		if (!time.find()) throw new IllegalArgumentException("The console line has no time: " + line);

		return LocalTime.parse(time.group());
	}
}
