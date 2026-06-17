package me.whereareiam.identica.replication.store.participant;

import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Participant notified when an account lifecycle operation clears or deletes an
 * account and any state that depends on it.
 */
public interface AccountLifecycleParticipant extends BoundaryParticipant {
	/**
	 * Handles an account lifecycle boundary.
	 *
	 * @param event account lifecycle event
	 */
	void onAccountLifecycle(@NotNull AccountLifecycleEvent event);
}
