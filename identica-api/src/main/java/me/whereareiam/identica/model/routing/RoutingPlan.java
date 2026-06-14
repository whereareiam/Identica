package me.whereareiam.identica.model.routing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.type.routing.RoutingPlanAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Planned routing state transition.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class RoutingPlan {
	private final @NotNull RoutingPlanAction action;
	private final @Nullable UUID connectionUniqueId;
	private final @Nullable RoutingIntent intent;

	/**
	 * Creates a start transition for the given routing intent.
	 *
	 * @param intent routing intent to start
	 * @return plan that starts the provided intent
	 */
	public static @NotNull RoutingPlan start(@NotNull RoutingIntent intent) {
		return new RoutingPlan(RoutingPlanAction.START, intent.getConnectionUniqueId(), intent);
	}

	/**
	 * Creates a replace transition for the given routing intent.
	 *
	 * @param intent routing intent to replace the current one with
	 * @return plan that replaces the current intent
	 */
	public static @NotNull RoutingPlan replace(@NotNull RoutingIntent intent) {
		return new RoutingPlan(RoutingPlanAction.REPLACE, intent.getConnectionUniqueId(), intent);
	}

	/**
	 * Creates a clear transition for the routing intent associated with a
	 * connection.
	 *
	 * @param connectionUniqueId connection unique id whose routing should clear
	 * @return plan that clears the current intent
	 */
	public static @NotNull RoutingPlan clear(@NotNull UUID connectionUniqueId) {
		return new RoutingPlan(RoutingPlanAction.CLEAR, connectionUniqueId, null);
	}

	/**
	 * Creates a no-op transition.
	 *
	 * @return plan that leaves routing state unchanged
	 */
	public static @NotNull RoutingPlan ignore() {
		return new RoutingPlan(RoutingPlanAction.IGNORE, null, null);
	}
}
