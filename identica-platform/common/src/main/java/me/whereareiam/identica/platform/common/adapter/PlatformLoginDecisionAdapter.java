package me.whereareiam.identica.platform.common.adapter;

import org.jetbrains.annotations.NotNull;

/**
 * Platform adapter that processes login-time connection decisions.
 *
 * <p>The login is where any refusal from a prepare stage is enforced. A platform whose profile stage cannot refuse
 * a connection relies on it: the refusal stored at the profile stage is found here and the player is refused before
 * Identica's flow starts.</p>
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
