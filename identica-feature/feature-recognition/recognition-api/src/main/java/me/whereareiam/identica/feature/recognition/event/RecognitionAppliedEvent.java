package me.whereareiam.identica.feature.recognition.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a session opens for an authentication that completed via
 * reconnect recognition.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class RecognitionAppliedEvent implements Event, SynchronousEvent {
	private final @NotNull UUID connectionUniqueId;
	private final @NotNull PipelineType pipelineType;
	private final @NotNull Session session;
}
