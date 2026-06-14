package me.whereareiam.identica.event.routing.intent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.event.routing.RoutingEvent;
import me.whereareiam.identica.model.routing.RoutingIntent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a connection-scoped routing intent is cleared.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class RoutingIntentClearedEvent implements RoutingEvent, SynchronousEvent {
	private final UUID connectionUniqueId;
	private final @Nullable RoutingIntent intent;
}
