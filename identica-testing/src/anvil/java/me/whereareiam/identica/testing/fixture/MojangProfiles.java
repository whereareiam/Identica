package me.whereareiam.identica.testing.fixture;

import com.sun.net.httpserver.HttpServer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for Mojang's profile lookup, which the Premium provider asks whether a username belongs to a premium
 * account. A test names the premium usernames, so its outcome does not depend on Mojang's service or on which
 * names happen to be taken. Every network points Premium's {@code lookup.profileEndpoint} here.
 *
 * <p>Only the lookup is replaced. A premium login itself is still verified by the proxy against Mojang's session
 * server.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MojangProfiles {
	private static final Set<String> PREMIUM = ConcurrentHashMap.newKeySet();
	private static final HttpServer SERVER = start();

	private static volatile boolean unavailable;

	/**
	 * Returns the endpoint template for Premium's settings, with {@code %s} for the username.
	 */
	public static String endpoint() {
		return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/users/profiles/minecraft/%s";
	}

	/**
	 * Forgets every premium username and makes the lookup available again; each new network starts from this.
	 */
	public static void reset() {
		PREMIUM.clear();
		unavailable = false;
	}

	/**
	 * Makes the lookup report a premium profile for the username, whatever its letter case.
	 */
	public static void premium(String username) {
		PREMIUM.add(username.toLowerCase(Locale.ROOT));
	}

	/**
	 * Makes every lookup fail with a server error, as during an outage of Mojang's service.
	 */
	public static void unavailable() {
		unavailable = true;
	}

	private static HttpServer start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/users/profiles/minecraft/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				String username = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
				int status = unavailable ? 503 : PREMIUM.contains(username) ? 200 : 404;
				byte[] body = status == 200 ? ("{\"name\":\"" + username + "\"}").getBytes() : new byte[0];
				exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
				if (body.length > 0) exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.setExecutor(null);
			server.start();
			return server;
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
