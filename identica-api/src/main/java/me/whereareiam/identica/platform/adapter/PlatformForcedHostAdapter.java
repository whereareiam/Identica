package me.whereareiam.identica.platform.adapter;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Platform adapter that reads the forced hosts configured on the proxy.
 *
 * <p>Routing asks this adapter for targets that follow forced hosts, so the
 * proxy's own configuration stays the only place where hostnames are mapped
 * to servers.</p>
 */
public interface PlatformForcedHostAdapter {
	/**
	 * Resolves the server the proxy forces for the hostname a connection
	 * joined through.
	 *
	 * <p>The hostname is the host of the connection's
	 * {@link ConnectionIdentity#getOrigin() origin} and is matched
	 * case-insensitively. When the proxy lists several servers for it, the
	 * first one is returned; the server is not checked for being registered
	 * or reachable.</p>
	 *
	 * @param connection connection to resolve the forced host for
	 * @return forced server name, or empty when the connection has no origin
	 *         or the proxy has no forced host for its hostname
	 */
	@NotNull Optional<String> resolve(@NotNull ConnectionIdentity connection);
}
