package me.whereareiam.identica.model.delivery;

import lombok.*;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Queued delivery work for a specific identity or connection. A request waits until the player's client joins a
 * server's world, announced by {@link me.whereareiam.identica.event.connection.ServerJoinedEvent}, since a client shows
 * no messages before. A request may require a specific server, which lets routing-sensitive flows wait until the
 * player reaches the intended backend before completion logic runs.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DeliveryRequest {
	private @NotNull UUID id;
	private @NotNull DeliverySource source;
	private @NotNull DeliveryTarget target;
	private @NotNull DeliveryPayload payload;
	private @NotNull DeliverySemantics semantics;
	private @Nullable DeliveryMarker marker;
	private @Nullable String requiredServer;
	private long createdAt;
	private long updatedAt;
}
