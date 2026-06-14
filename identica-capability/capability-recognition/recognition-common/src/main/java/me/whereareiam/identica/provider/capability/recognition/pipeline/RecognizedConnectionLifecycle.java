package me.whereareiam.identica.provider.capability.recognition.pipeline;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.provider.capability.recognition.store.RecognizedConnectionStore;
import org.jetbrains.annotations.NotNull;

// TODO Rewrite

@Singleton
public class RecognizedConnectionLifecycle implements EventListener {
	private final RecognizedConnectionStore recognizedConnectionStore;

	@Inject
	public RecognizedConnectionLifecycle(
			@NotNull RecognizedConnectionStore recognizedConnectionStore,
			@NotNull EventManager eventManager
	) {
		this.recognizedConnectionStore = recognizedConnectionStore;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionCompleted(@NotNull ConnectionCompletedEvent event) {
		recognizedConnectionStore.consumeRecognized(event.getConnectionUniqueId());
	}

	@IdenticEvent
	public void onConnectionTerminated(@NotNull ConnectionTerminatedEvent event) {
		recognizedConnectionStore.consumeRecognized(event.getConnectionUniqueId());
	}
}
