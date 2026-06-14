package me.whereareiam.identica.common.connection;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.handshake.DefaultHandshakeStore;
import me.whereareiam.identica.common.provider.DefaultProviderAttemptStore;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.provider.ProviderAttemptStore;

public class ConnectionStateConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(HandshakeStore.class).to(DefaultHandshakeStore.class).asEagerSingleton();
		bind(ProviderAttemptStore.class).to(DefaultProviderAttemptStore.class).asEagerSingleton();
		bind(ConnectionLifecycleService.class).to(DefaultConnectionLifecycleService.class).asEagerSingleton();
	}
}
