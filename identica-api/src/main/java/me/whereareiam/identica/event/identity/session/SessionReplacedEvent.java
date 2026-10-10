package me.whereareiam.identica.event.identity.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.model.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired on the proxy that opens a session replacing the session of another connection of the same
 * account. The replaced session is then closed through a replicated {@link SessionClosedEvent} that
 * disconnects its connection on the proxy that holds it.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class SessionReplacedEvent implements Event, SynchronousEvent {
	private final @NotNull Session existingSession;
	private final @NotNull Session replacementSession;
}
