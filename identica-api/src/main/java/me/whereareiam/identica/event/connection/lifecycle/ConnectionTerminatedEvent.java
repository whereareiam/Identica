package me.whereareiam.identica.event.connection.lifecycle;

import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when Identica aborts a connection journey before it successfully
 * completes.
 *
 * <p>Listeners should treat this as journey-level termination rather than a generic
 * physical disconnect.</p>
 */
public class ConnectionTerminatedEvent extends ConnectionLifecycleEvent {
	public ConnectionTerminatedEvent(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	) {
		super(connectionUniqueId, accountUniqueId, pipelineType);
	}
}
