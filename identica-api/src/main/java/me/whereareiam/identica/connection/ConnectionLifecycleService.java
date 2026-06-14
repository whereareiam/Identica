package me.whereareiam.identica.connection;

import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Emits connection lifecycle transitions for physical disconnects and journey
 * finalization.
 */
public interface ConnectionLifecycleService {
	/**
	 * Emits a physical disconnect event for the current connection.
	 *
	 * @param connectionUniqueId live connection unique id
	 * @param accountUniqueId resolved account unique id when known
	 * @param pipelineType pipeline type that was active when known
	 */
	void disconnected(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	);

	/**
	 * Marks the connection as fully completed and emits a completion event exactly once
	 * for the current journey.
	 *
	 * @param connectionUniqueId live connection unique id
	 * @param accountUniqueId resolved account unique id when known
	 * @param pipelineType pipeline type that completed when known
	 */
	void completed(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	);

	/**
	 * Marks the connection as terminated and emits a termination event exactly once for
	 * the current journey.
	 *
	 * @param connectionUniqueId live connection unique id
	 * @param accountUniqueId resolved account unique id when known
	 * @param pipelineType pipeline type that was active when known
	 */
	void terminated(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@Nullable PipelineType pipelineType
	);
}
