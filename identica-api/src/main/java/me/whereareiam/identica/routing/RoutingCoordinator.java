package me.whereareiam.identica.routing;

import me.whereareiam.identica.model.routing.RoutingSignal;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Common entry point for routing lifecycle transitions.
 * <p>
 * Pipeline code submits routing facts through {@link #accept(RoutingSignal)}.
 * Platform adapters report reached servers through {@link #markReached(UUID, String)}
 * and clear connection-scoped routing through {@link #clear(UUID)}.
 * Implementations own intent planning, persistence, and routing event publication.
 */
public interface RoutingCoordinator {
	/**
	 * Accepts a pipeline routing signal and applies the resulting routing plan.
	 *
	 * @param signal pipeline fact that may create, replace, clear, or ignore routing
	 */
	void accept(@NotNull RoutingSignal signal);

	/**
	 * Marks a routing intent as reached when the connection arrives on the expected
	 * server.
	 *
	 * @param connectionUniqueId connection unique id
	 * @param serverName current platform server name
	 */
	void markReached(@NotNull UUID connectionUniqueId, @NotNull String serverName);

	/**
	 * Clears the routing intent for a connection and publishes the clear event.
	 *
	 * @param connectionUniqueId connection unique id
	 */
	void clear(@NotNull UUID connectionUniqueId);
}
