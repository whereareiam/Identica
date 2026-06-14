package me.whereareiam.identica.provider.capability.recognition.pipeline;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionCompletedEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionTerminatedEvent;
import me.whereareiam.identica.provider.capability.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

@DisplayName("Recognized Connection Lifecycle")
class RecognizedConnectionLifecycleTest {
	@DisplayName("Completion and termination both consume recognized connection state")
	@Test
	void completionAndTerminationBothConsumeRecognizedConnectionState() {
		RecognizedConnectionStore store = mock(RecognizedConnectionStore.class);
		EventManager eventManager = mock(EventManager.class);
		RecognizedConnectionLifecycle lifecycle = new RecognizedConnectionLifecycle(store, eventManager);
		UUID connectionUniqueId = UUID.randomUUID();

		lifecycle.onConnectionCompleted(new ConnectionCompletedEvent(connectionUniqueId, null, PipelineType.AUTHENTICATION));
		lifecycle.onConnectionTerminated(new ConnectionTerminatedEvent(connectionUniqueId, null, PipelineType.AUTHENTICATION));

		verify(store, times(2)).consumeRecognized(connectionUniqueId);
	}
}
