package me.whereareiam.identica.platform.common.adapter;

import org.jetbrains.annotations.NotNull;

/**
 * Platform adapter that applies profile preparation or rewrite behavior.
 *
 * @param <E> platform event type
 */
public interface PlatformProfileAdapter<E> {
	/**
	 * Applies platform profile behavior to the given event.
	 *
	 * @param event platform event
	 */
	void apply(@NotNull E event);
}
