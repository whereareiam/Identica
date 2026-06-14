package me.whereareiam.identica.model.routing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.routing.RoutingIntentStatus;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stateful route command for one connection.
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class RoutingIntent {
	private final @NotNull UUID id;
	private final @NotNull UUID connectionUniqueId;

	private final @NotNull RoutingEndpoint endpoint;
	private final @NotNull RoutingReason reason;
	private final @NotNull RoutingAttemptPolicy attemptPolicy;
	private final @NotNull RoutingAttemptState attemptState;

	private @NotNull RoutingIntentStatus status = RoutingIntentStatus.PENDING;

	private final @Nullable PipelineType pipelineType;
	private final @Nullable StageType stage;
	private final @Nullable String providerId;
	private final @Nullable String stepName;

	private final long createdAt;
	private long updatedAt;
}
