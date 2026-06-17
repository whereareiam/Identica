package me.whereareiam.identica.common.replication.store;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import org.jetbrains.annotations.NotNull;

@Singleton
public final class BoundaryParticipantCoordinator implements EventListener {
	private final Registry<ConnectionDisconnectedParticipant> disconnectedParticipants;
	private final Registry<ConnectionCompletedParticipant> completedParticipants;
	private final Registry<ConnectionTerminatedParticipant> terminatedParticipants;
	private final Registry<AccountLifecycleParticipant> accountParticipants;

	@Inject
	public BoundaryParticipantCoordinator(
			@NotNull Registry<ConnectionDisconnectedParticipant> disconnectedParticipants,
			@NotNull Registry<ConnectionCompletedParticipant> completedParticipants,
			@NotNull Registry<ConnectionTerminatedParticipant> terminatedParticipants,
			@NotNull Registry<AccountLifecycleParticipant> accountParticipants,
			@NotNull EventManager eventManager
	) {
		this.disconnectedParticipants = disconnectedParticipants;
		this.completedParticipants = completedParticipants;
		this.terminatedParticipants = terminatedParticipants;
		this.accountParticipants = accountParticipants;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		for (ConnectionDisconnectedParticipant participant : disconnectedParticipants.values())
			participant.onConnectionDisconnected(event);
	}

	@IdenticEvent
	public void onConnectionCompleted(@NotNull ConnectionCompletedEvent event) {
		for (ConnectionCompletedParticipant participant : completedParticipants.values())
			participant.onConnectionCompleted(event);
	}

	@IdenticEvent
	public void onConnectionTerminated(@NotNull ConnectionTerminatedEvent event) {
		for (ConnectionTerminatedParticipant participant : terminatedParticipants.values())
			participant.onConnectionTerminated(event);
	}

	@IdenticEvent
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		for (AccountLifecycleParticipant participant : accountParticipants.values())
			participant.onAccountLifecycle(event);
	}
}
