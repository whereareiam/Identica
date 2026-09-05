package me.whereareiam.identica.feature.recognition.pipeline;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.feature.recognition.event.RecognitionAppliedEvent;
import me.whereareiam.identica.feature.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RecognitionAppliedLifecycle implements EventListener {
	private final EventManager eventManager;
	private final RecognizedConnectionStore recognizedConnectionStore;

	@IdenticEvent
	public void onSessionOpened(@NotNull SessionOpenedEvent event) {
		if (event.getPipelineType() != PipelineType.AUTHENTICATION) return;
		if (!recognizedConnectionStore.isRecognized(event.getConnectionUniqueId())) return;

		eventManager.call(new RecognitionAppliedEvent(
				event.getConnectionUniqueId(),
				event.getPipelineType(),
				event.getSession()
		));
	}
}
