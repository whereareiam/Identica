package me.whereareiam.identica.model.routing.execution;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.routing.RoutingConnectionSnapshot;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
import org.jetbrains.annotations.NotNull;

/**
 * Common routing request passed to a platform routing adapter.
 */
@Getter
@RequiredArgsConstructor
public class RoutingRequest {
	private final @NotNull RoutingIntent intent;
	private final @NotNull RoutingConnectionSnapshot connection;
	private final @NotNull RoutingAttemptTrigger trigger;
}
