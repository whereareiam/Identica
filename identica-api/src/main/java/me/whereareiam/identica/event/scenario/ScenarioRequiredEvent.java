package me.whereareiam.identica.event.scenario;

import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base marker for scenario events fired when a player is now held by a
 * scenario that must be completed or resumed.
 */
public interface ScenarioRequiredEvent extends Event {
	/**
	 * Returns the connection unique id for the pending scenario holder.
	 *
	 * @return connection unique id
	 */
	@NotNull UUID getConnectionUniqueId();

	/**
	 * Returns the resolved account unique id when it is already known.
	 *
	 * @return resolved account unique id or {@code null}
	 */
	@Nullable UUID getAccountUniqueId();

	/**
	 * Returns the concrete scenario context that entered the pending state.
	 *
	 * @return scenario context
	 */
	@NotNull ScenarioContext getContext();

	/**
	 * Returns whether the pending state belongs to a resumed scenario attempt.
	 *
	 * @return {@code true} when this pending scenario came from a resume
	 */
	boolean isResumed();

	/**
	 * Returns the absolute expiration timestamp for the held scenario state.
	 *
	 * @return expiration timestamp in milliseconds since epoch
	 */
	long getExpiresAt();
}
