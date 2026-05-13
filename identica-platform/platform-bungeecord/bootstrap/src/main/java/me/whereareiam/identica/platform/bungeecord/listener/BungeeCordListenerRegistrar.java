package me.whereareiam.identica.platform.bungeecord.listener;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.common.CommonListenerRegistrar;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.listener.DynamicListenerRegistry;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.platform.bungeecord.BungeeCordIdentica;
import me.whereareiam.identica.platform.bungeecord.listener.connection.LoginListener;
import me.whereareiam.identica.platform.bungeecord.listener.connection.PlayerDisconnectListener;
import me.whereareiam.identica.platform.bungeecord.listener.connection.PlayerHandshakeListener;
import me.whereareiam.identica.platform.bungeecord.listener.connection.PostLoginListener;
import me.whereareiam.identica.platform.bungeecord.listener.connection.server.ServerConnectListener;
import me.whereareiam.identica.platform.bungeecord.listener.connection.server.ServerConnectedListener;
import me.whereareiam.identica.platform.bungeecord.listener.connection.server.ServerSwitchListener;
import net.md_5.bungee.api.event.*;

@Singleton
public class BungeeCordListenerRegistrar extends CommonListenerRegistrar {
	private final Injector injector;
	private final DynamicListenerRegistry listenerRegistry;
	private final DynamicListenerRegistrar dynamicListenerRegistrar;

	@Inject
	public BungeeCordListenerRegistrar(
			Injector injector,
			Provider<Settings> settingsProvider,
			BungeeCordIdentica plugin,
			DynamicListenerRegistry listenerRegistry,
			DynamicListenerRegistrar dynamicListenerRegistrar
	) {
		super(settingsProvider);
		this.injector = injector;
		this.listenerRegistry = listenerRegistry;
		this.dynamicListenerRegistrar = dynamicListenerRegistrar;
	}

	@Override
	public void registerListeners() {
		listenerRegistry.attachRegistrar(this);

		registerListener(LoginEvent.class, injector.getInstance(LoginListener.class));
		registerListener(PlayerHandshakeEvent.class, injector.getInstance(PlayerHandshakeListener.class));
		registerListener(PostLoginEvent.class, injector.getInstance(PostLoginListener.class));
		registerListener(ServerConnectedEvent.class, injector.getInstance(ServerConnectedListener.class));
		registerListener(ServerSwitchEvent.class, injector.getInstance(ServerSwitchListener.class));
		registerListener(ServerConnectEvent.class, injector.getInstance(ServerConnectListener.class));
		registerListener(PlayerDisconnectEvent.class, injector.getInstance(PlayerDisconnectListener.class));
	}

	@Override
	public <T> void registerListener(Class<T> eventClass, DynamicListener<T> listener) {
		dynamicListenerRegistrar.register(eventClass, listener);
	}
}
