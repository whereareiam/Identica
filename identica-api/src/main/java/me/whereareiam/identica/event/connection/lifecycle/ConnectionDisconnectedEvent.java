package me.whereareiam.identica.event.connection.lifecycle;

import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when the physical player connection disconnects from the proxy.
 *
 * <p>Listeners should treat this as transport-level teardown for connection-scoped
 * state that should not survive a disconnect.</p>
 */
public class ConnectionDisconnectedEvent extends ConnectionLifecycleEvent {
	public ConnectionDisconnectedEvent(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	) {
		super(connectionUniqueId, accountUniqueId, pipelineType);
	}
}
