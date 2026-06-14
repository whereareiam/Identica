package me.whereareiam.identica.common.prepare;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import org.jetbrains.annotations.NotNull;

// TODO Rewrite

@Singleton
public class PrepareStateLifecycle implements EventListener {
	private final PrepareStateStore prepareStateStore;

	@Inject
	public PrepareStateLifecycle(
			@NotNull PrepareStateStore prepareStateStore,
			@NotNull EventManager eventManager
	) {
		this.prepareStateStore = prepareStateStore;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		prepareStateStore.clear(event.getConnectionUniqueId());
	}
}
