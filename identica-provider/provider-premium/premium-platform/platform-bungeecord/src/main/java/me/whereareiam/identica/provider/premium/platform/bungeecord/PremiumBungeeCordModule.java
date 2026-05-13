package me.whereareiam.identica.provider.premium.platform.bungeecord;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.provider.premium.platform.bungeecord.handshake.PremiumBungeeCordHandshakeApplierLifecycle;
import me.whereareiam.identica.provider.premium.platform.bungeecord.handshake.PremiumHandshakeApplier;
import me.whereareiam.identica.provider.premium.platform.bungeecord.listener.connection.PremiumPostLoginListener;

public class PremiumBungeeCordModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(PremiumPostLoginListener.class).asEagerSingleton();
		bind(PremiumHandshakeApplier.class).asEagerSingleton();
		bind(PremiumBungeeCordHandshakeApplierLifecycle.class).asEagerSingleton();
	}
}
