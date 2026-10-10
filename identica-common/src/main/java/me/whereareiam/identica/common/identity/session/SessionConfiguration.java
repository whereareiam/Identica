package me.whereareiam.identica.common.identity.session;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.identity.session.SessionService;

public class SessionConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(SessionService.class).to(DefaultSessionService.class).asEagerSingleton();
	}
}
