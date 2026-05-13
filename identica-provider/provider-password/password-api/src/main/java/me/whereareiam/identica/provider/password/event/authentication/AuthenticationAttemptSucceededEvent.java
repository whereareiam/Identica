package me.whereareiam.identica.provider.password.event.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.provider.password.model.authentication.AuthenticationAttemptContext;

@Getter
@AllArgsConstructor
public class AuthenticationAttemptSucceededEvent implements Event, SynchronousEvent {
	private final AuthenticationAttemptContext context;
}
