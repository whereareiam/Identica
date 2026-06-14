package me.whereareiam.identica.platform.adapter;

import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.execution.RoutingOutcome;
import me.whereareiam.identica.model.routing.execution.RoutingRequest;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Platform-specific adapter for executing routing intents against the live proxy runtime.
 *
 * <p>Each platform must bind exactly one implementation. The common routing runtime owns
 * retry, failure, and cleanup policy; platform adapters only translate proxy behavior into
 * normalized routing snapshots and execution outcomes.</p>
 */
public interface PlatformRoutingAdapter {
	/**
	 * Returns the current live routing snapshot for a connection when the player is still
	 * known to the platform.
	 *
	 * @param connectionUniqueId live connection unique id
	 * @return current routing snapshot when the player is available to the platform
	 */
	@NotNull Optional<RoutingConnectionSnapshot> snapshot(@NotNull UUID connectionUniqueId);

	/**
	 * Routes a connection on the platform and reports the normalized outcome.
	 *
	 * @param request routing request
	 * @return asynchronous normalized routing outcome
	 */
	@NotNull CompletionStage<RoutingOutcome> route(@NotNull RoutingRequest request);

	/**
	 * Disconnects the live connection with the provided rendered message.
	 *
	 * @param connectionUniqueId live connection unique id
	 * @param message rendered disconnect message
	 */
	void disconnect(@NotNull UUID connectionUniqueId, @NotNull Component message);
}
