package me.whereareiam.identica.platform.common.adapter;

import org.jetbrains.annotations.NotNull;

/**
 * Platform adapter that processes login-time connection decisions.
 *
 * @param <E> platform event type
 */
public interface PlatformLoginDecisionAdapter<E> {
	/**
	 * Processes a login event.
	 *
	 * @param event platform event
	 */
	void process(@NotNull E event);
}
