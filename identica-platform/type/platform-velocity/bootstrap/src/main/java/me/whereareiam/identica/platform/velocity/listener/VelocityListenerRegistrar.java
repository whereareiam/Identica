package me.whereareiam.identica.platform.velocity.listener;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import me.whereareiam.identica.common.CommonListenerRegistrar;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.listener.DynamicListenerRegistry;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.platform.velocity.VelocityIdentica;
import me.whereareiam.identica.platform.velocity.checkpoint.InitialServerHoldListener;
import me.whereareiam.identica.platform.velocity.checkpoint.ProxyHoldKeepAlive;
import me.whereareiam.identica.platform.velocity.listener.connection.DisconnectListener;
import me.whereareiam.identica.platform.velocity.listener.connection.GameProfileRequestListener;
import me.whereareiam.identica.platform.velocity.listener.connection.LoginListener;
import me.whereareiam.identica.platform.velocity.listener.connection.PreLoginListener;
import me.whereareiam.identica.platform.velocity.listener.connection.server.PlayerChooseInitialServerListener;
import me.whereareiam.identica.platform.velocity.listener.connection.server.ServerPreConnectListener;
import me.whereareiam.identica.platform.velocity.listener.connection.server.ServerConnectedListener;
import me.whereareiam.identica.platform.velocity.listener.connection.server.ServerPostConnectListener;
import me.whereareiam.identica.platform.velocity.util.VelocityUtil;

@Singleton
public class VelocityListenerRegistrar extends CommonListenerRegistrar {
	private final Injector injector;
	private final VelocityIdentica plugin;
	private final EventManager eventManager;
	private final DynamicListenerRegistry listenerRegistry;

	@Inject
	public VelocityListenerRegistrar(
			Injector injector,
			Provider<Settings> settingsProvider,
			VelocityIdentica plugin,
			EventManager eventManager,
			DynamicListenerRegistry listenerRegistry
	) {
		super(settingsProvider);
		this.injector = injector;
		this.plugin = plugin;
		this.eventManager = eventManager;
		this.listenerRegistry = listenerRegistry;
	}

	@Override
	public void registerListeners() {
		listenerRegistry.attachRegistrar(this);

		registerAwaitingListener(PreLoginEvent.class, injector.getInstance(PreLoginListener.class));
		registerListener(GameProfileRequestEvent.class, injector.getInstance(GameProfileRequestListener.class));
		registerListener(LoginEvent.class, injector.getInstance(LoginListener.class));
		registerListener(ServerConnectedEvent.class, injector.getInstance(ServerConnectedListener.class));
		registerListener(ServerPostConnectEvent.class, injector.getInstance(ServerPostConnectListener.class));
		registerListener(PlayerChooseInitialServerEvent.class, injector.getInstance(PlayerChooseInitialServerListener.class));
		registerListener(ServerPreConnectEvent.class, injector.getInstance(ServerPreConnectListener.class));
		registerListener(DisconnectEvent.class, injector.getInstance(DisconnectListener.class));
		registerCheckpointHold();
	}

	/**
	 * Holds players on the proxy before their first server while a checkpoint has their waiting step. The hold runs
	 * before Identica chooses the initial server, which then follows the journey's completion routing.
	 */
	private void registerCheckpointHold() {
		InitialServerHoldListener hold = injector.getInstance(InitialServerHoldListener.class);
		ProxyHoldKeepAlive keepAlive = injector.getInstance(ProxyHoldKeepAlive.class);
		eventManager.register(plugin, PlayerChooseInitialServerEvent.class, PostOrder.FIRST, hold);
		eventManager.register(plugin, DisconnectEvent.class, PostOrder.LAST, hold::left);
		eventManager.register(plugin, CookieReceiveEvent.class, PostOrder.FIRST, keepAlive::answered);
	}

	@Override
	public <T> void registerListener(Class<T> eventClass, DynamicListener<T> listener) {
		if (shouldRegister(eventClass)) return;

		Logger.debug("Registering listener for event " + eventClass.getName());
		eventManager.register(plugin, eventClass, VelocityUtil.of(determinePriority(eventClass)), listener::onEvent);
	}

	@SuppressWarnings("SameParameterValue")
	private <T> void registerAwaitingListener(Class<T> eventClass, AwaitingEventExecutor<T> listener) {
		if (shouldRegister(eventClass)) return;
		Logger.debug("Registering listener for event " + eventClass.getName());
		eventManager.register(plugin, eventClass, VelocityUtil.of(determinePriority(eventClass)), listener);
	}

	private boolean shouldRegister(Class<?> eventClass) {
		var registration = settings
				.get()
				.getListeners()
				.getEvents()
				.get(eventClass.getName());

		return registration != null && !registration.isRegister();
	}
}
