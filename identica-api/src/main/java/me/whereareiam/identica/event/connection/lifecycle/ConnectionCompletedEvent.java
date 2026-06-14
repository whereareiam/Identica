package me.whereareiam.identica.event.connection.lifecycle;

import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a connection successfully finishes its entire journey, including
 * completion runtime.
 */
public class ConnectionCompletedEvent extends ConnectionLifecycleEvent {
	public ConnectionCompletedEvent(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	) {
		super(connectionUniqueId, accountUniqueId, pipelineType);
	}
}
