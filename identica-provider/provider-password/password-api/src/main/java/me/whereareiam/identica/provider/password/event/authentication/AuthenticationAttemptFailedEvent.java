package me.whereareiam.identica.provider.password.event.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.provider.password.model.authentication.AuthenticationAttemptContext;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@AllArgsConstructor
public class AuthenticationAttemptFailedEvent implements Event, SynchronousEvent {
	private final AuthenticationAttemptContext context;
	private @Nullable AuthenticationAttemptDecision decision;
}
