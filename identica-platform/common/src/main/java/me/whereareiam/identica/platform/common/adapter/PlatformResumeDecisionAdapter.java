package me.whereareiam.identica.platform.common.adapter;

import org.jetbrains.annotations.NotNull;

/**
 * Platform adapter that processes first-connect resume decisions.
 *
 * @param <E> platform event type
 */
public interface PlatformResumeDecisionAdapter<E> {
	/**
	 * Processes a resume event.
	 *
	 * @param event platform event
	 */
	void resume(@NotNull E event);
}
