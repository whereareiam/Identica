package me.whereareiam.identica.common.completion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.pipeline.completion.CompletionPendingStore;
import org.jetbrains.annotations.NotNull;

// TODO Rewrite

@Singleton
public class PendingCompletionLifecycle implements EventListener {
	private final CompletionPendingStore completionPendingStore;

	@Inject
	public PendingCompletionLifecycle(
			@NotNull CompletionPendingStore completionPendingStore,
			@NotNull EventManager eventManager
	) {
		this.completionPendingStore = completionPendingStore;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		completionPendingStore.clear(event.getConnectionUniqueId());
	}
}
